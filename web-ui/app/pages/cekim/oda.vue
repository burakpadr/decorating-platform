<script setup lang="ts">
/**
 * Stage 2's capture screen: one area at a time, one frame at a time (§2.4–2.5, BOYA-42).
 *
 * This is the screen the whole of stage 2 exists for, and the one that makes BOYA-40's presigned upload
 * and BOYA-41's pipeline visible to anybody. §2.5 checks each frame on the phone "while the customer is
 * still in the room", which is the entire reason the check is here rather than in the analysis hours
 * later.
 *
 * §2.4 says "önizlenir, onaylanır" and there is no confirm step in this page, on purpose. An
 * `<input capture>` opens the native camera, and iOS and Android both hand the file back only after
 * their own use-or-retake screen — so a preview here asked the same question a second time, 28 times
 * over. It was built that way first and using it is what settled it.
 *
 * A frame the check *refuses* is still shown, because there the page knows something the camera did
 * not: seeing the dark photograph is what makes "ışıkları açın" mean anything.
 *
 * §2.4 opens with "Listeden alan seçilir" and that is a requirement, not a stage direction. The
 * customer is walking their own home, in whatever order its doors are in, holding the phone. A screen
 * that marched them frame by frame in the order the server derived would be arguing with the person who
 * can actually see the room — and the first thing they would do is photograph the wrong wall to get
 * past it. So the area is theirs to open and the frame is theirs to choose; the outstanding one is
 * merely where the screen opens.
 *
 * The server owns where the capture has got to. §10 is explicit that this state cannot live in
 * localStorage — people abandon mid-flow and resume on another device, and the desktop-to-mobile handoff
 * is the normal way this screen is reached — so every accepted frame is re-read from
 * GET /rooms rather than tracked here. The one thing held locally is the attempt count, because §9's
 * three-rejection rule is about this sitting, not about the request: a frame refused today and retried
 * tomorrow deserves the same three honest attempts.
 *
 * Not here: the close-ups of §2.6 (BOYA-43), the retry queue of §2.7 (BOYA-44, so a failed upload is
 * told to the customer rather than queued), and what happens once every frame is in — §3 needs a
 * verified phone before ANALYZING, which is BOYA-45.
 */
import type { components } from '@decorating/api-client'

/** The roles the contract accepts. Taken from it rather than restated, so a new frame kind cannot
 * appear on the server and be silently unsendable from here. */
type PhotoRole = components['schemas']['UploadIntentRequest']['role']

const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.capture.title') }) })

const id = String(route.query.talep ?? '')

const { data: state, error: loadError, refresh } = await useAsyncData('capture-state', async () => {
  const { data, response } = await api.GET('/api/quote-requests/{id}/rooms', {
    params: { path: { id } },
  })
  if (!response.ok) {
    throw new Error('capture')
  }
  return data ?? null
})

/** Where the screen opens: the first area still owing frames. A starting point, never a constraint. */
const suggested = computed(() => {
  if (!state.value) {
    return null
  }
  const at = nextOutstanding(state.value)
  return at === null ? null : state.value.areas[at.areaIndex]!.id
})

const opened = ref<string | null>(null)
const openAreaId = computed(() => opened.value ?? suggested.value ?? state.value?.areas[0]?.id ?? null)
const openArea = computed(() =>
  state.value?.areas.find(a => a.id === openAreaId.value) ?? null)

/** The frame the customer tapped, waiting for the picker to hand a file back. */
const pending = ref<{ areaId: string, role: PhotoRole } | null>(null)

/**
 * The frame that was refused, kept on screen until another is taken (§2.5).
 *
 * The blob shown is the processed one, not what came off the camera: it is what was measured and what
 * the analysis would have been given. The object URL is revoked whenever this is replaced or cleared,
 * or a capture of 28 frames leaves decoded images pinned in a phone's memory.
 */
const refused = ref<{ role: PhotoRole, url: string, reason: string } | null>(null)

function clearRefused() {
  if (refused.value !== null) {
    URL.revokeObjectURL(refused.value.url)
    refused.value = null
  }
}

onBeforeUnmount(clearRefused)

const camera = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const stage = ref<'idle' | 'preparing' | 'uploading'>('idle')
const percent = ref(0)
const failed = ref(false)
const needsConsent = ref(false)

/**
 * How many times this exact frame has been offered in this sitting.
 *
 * Keyed by area and role rather than by position: the position moves as frames are accepted, and a
 * counter that moved with it would reset §9's three attempts every time the customer went back.
 */
const attempts = ref<Record<string, number>>({})

const guideLink = computed(() => `/cekim/rehber?talep=${encodeURIComponent(id)}`)
const roomsLink = computed(() => `/cekim?talep=${encodeURIComponent(id)}`)
const verifyLink = computed(() => `/cekim/dogrulama?talep=${encodeURIComponent(id)}`)

function labelOf(areaType: string, role: string): string {
  return t(frameLabelKey(areaType, role))
}

/** The hints for the kinds of frame this area asks for, each said once rather than per row. */
function hintsFor(areaType: string, frames: { role: string }[]): string[] {
  return [...new Set(frames.map(f => frameHintKey(areaType, f.role)))].map(key => t(key))
}

function openOnly(areaId: string) {
  opened.value = areaId
  clearRefused()
  failed.value = false
  needsConsent.value = false
}

/** Tapping a frame is what opens the camera, so the tap has to say which frame it was. */
function shoot(areaId: string, role: PhotoRole) {
  if (busy.value) {
    return
  }
  pending.value = { areaId, role }
  clearRefused()
  failed.value = false
  needsConsent.value = false
  camera.value?.click()
}

async function onFileChosen(event: Event) {
  const chosen = (event.target as HTMLInputElement).files?.[0]
  // Clear the input, or choosing the same file twice after a rejection fires no change event at all.
  ;(event.target as HTMLInputElement).value = ''
  const target = pending.value
  if (!chosen || busy.value || target === null) {
    return
  }

  busy.value = true
  clearRefused()
  failed.value = false
  needsConsent.value = false
  percent.value = 0
  const key = `${target.areaId}:${target.role}`
  const attempt = (attempts.value[key] ?? 0) + 1

  try {
    stage.value = 'preparing'
    const processed = await processFrame(chosen, target.role, attempt)

    // §2.5 speaks while the customer is still standing in the room, and shows them the frame it is
    // complaining about — "ışıkları açın" against a photograph is advice; on its own it is a scolding.
    if (!processed.verdict.accept) {
      attempts.value = { ...attempts.value, [key]: attempt }
      refused.value = {
        role: target.role,
        url: URL.createObjectURL(processed.blob),
        reason: processed.verdict.reason!,
      }
      return
    }

    const intent = await api.POST('/api/photos/upload-intent', {
      body: { roomId: target.areaId, role: target.role },
    })
    if (intent.response.status === 403) {
      // The data notice has not been agreed to, or was withdrawn (BOYA-39). Nothing to retry here.
      needsConsent.value = true
      return
    }
    if (!intent.response.ok || !intent.data) {
      failed.value = true
      return
    }

    stage.value = 'uploading'
    await uploadFrame(intent.data.uploadUrl, processed.blob, {
      onProgress: fraction => (percent.value = Math.round(fraction * 100)),
    })

    const done = await api.POST('/api/photos/{id}/complete', {
      params: { path: { id: intent.data.photoId } },
      body: processed.measurements,
    })
    if (!done.response.ok) {
      failed.value = true
      return
    }

    attempts.value = { ...attempts.value, [key]: 0 }
    await refresh()
  }
  catch {
    failed.value = true
  }
  finally {
    busy.value = false
    stage.value = 'idle'
  }
}

/** §9 refuses to overwrite an uploaded frame, so a retake deletes the object and starts again. */
async function retake(photoId: string) {
  if (busy.value) {
    return
  }
  busy.value = true
  failed.value = false
  try {
    const { response } = await api.DELETE('/api/photos/{id}', { params: { path: { id: photoId } } })
    if (!response.ok) {
      failed.value = true
      return
    }
    await refresh()
  }
  catch {
    failed.value = true
  }
  finally {
    busy.value = false
  }
}
</script>

<template>
  <main>
    <template v-if="!id">
      <p class="panel">{{ t('capture.incomplete') }}</p>
      <NuxtLink class="btn outline" to="/teklif-al">{{ t('capture.goToForm') }}</NuxtLink>
    </template>

    <template v-else-if="loadError">
      <p class="err" role="alert">{{ t('capture.failedToLoad') }}</p>
    </template>

    <template v-else-if="state">
      <p class="eyebrow">{{ t('capture.eyebrow') }}</p>

      <template v-if="state.areas.length === 0">
        <p class="panel">{{ t('capture.noAreas') }}</p>
        <NuxtLink class="btn outline" :to="roomsLink">{{ t('capture.goToRooms') }}</NuxtLink>
      </template>

      <template v-else>
        <p class="progress">{{ t('capture.progress', { taken: state.taken, required: state.required }) }}</p>
        <div class="bar" role="img" :aria-label="t('capture.progress', { taken: state.taken, required: state.required })">
          <i :style="{ width: `${state.required ? (state.taken / state.required) * 100 : 0}%` }" />
        </div>

        <template v-if="state.complete">
          <h1>{{ t('capture.doneTitle') }}</h1>
          <p class="intro">{{ t('capture.doneBody', { required: state.required }) }}</p>
          <NuxtLink class="btn primary verify" :to="verifyLink">
            {{ t('capture.goToVerify') }}
          </NuxtLink>
        </template>
        <h1 v-else>{{ t('capture.pickTitle') }}</h1>

        <p v-if="failed" class="err" role="alert">{{ t('capture.failed') }}</p>
        <template v-if="needsConsent">
          <p class="err" role="alert">{{ t('capture.consentNeeded') }}</p>
          <NuxtLink class="btn outline" :to="guideLink">{{ t('capture.goToGuide') }}</NuxtLink>
        </template>

        <section v-if="refused" class="refused">
          <img :src="refused.url" :alt="t('capture.refusedAlt')">
          <p class="err" role="alert">{{ t(`capture.rejected.${refused.reason}`) }}</p>
        </section>

        <p v-if="stage === 'preparing'" class="working">{{ t('capture.preparing') }}</p>
        <p v-else-if="stage === 'uploading'" class="working">
          {{ t('capture.uploading', { percent }) }}
        </p>

        <!-- §10's camera: opens the device camera on iOS Safari and Android Chrome, no native app. -->
        <input
          ref="camera"
          class="camera"
          type="file"
          accept="image/*"
          capture="environment"
          :disabled="busy"
          @change="onFileChosen"
        >

        <ol class="areas">
          <li v-for="a in state.areas" :key="a.id" class="area" :data-complete="a.complete">
            <button
              class="area-pick"
              type="button"
              :data-area="a.id"
              :aria-expanded="a.id === openAreaId"
              @click="openOnly(a.id)"
            >
              <span class="label">{{ a.label }}</span>
              <span class="count">{{ t('capture.areaProgress', {
                taken: a.frames.filter(f => f.taken).length, total: a.frames.length }) }}</span>
            </button>

            <template v-if="a.id === openAreaId">
              <p v-for="hint in hintsFor(a.type, a.frames)" :key="hint" class="hint">{{ hint }}</p>

              <ul class="frames">
                <li v-for="f in a.frames" :key="f.role" class="frameRow" :data-taken="f.taken">
                  <span class="frameName">{{ labelOf(a.type, f.role) }}</span>
                  <span v-if="f.lowQualityFlag" class="flag">{{ t('capture.lowQuality') }}</span>
                  <button
                    v-if="!f.taken"
                    class="btn primary shoot"
                    type="button"
                    :data-role="f.role"
                    :disabled="busy"
                    @click="shoot(a.id, f.role)"
                  >{{ t('capture.take') }}</button>
                  <button
                    v-else-if="f.photoId"
                    class="retake"
                    type="button"
                    :disabled="busy"
                    @click="retake(f.photoId)"
                  >{{ t('capture.retake') }}</button>
                </li>
              </ul>
            </template>
          </li>
        </ol>
      </template>
    </template>

    <p v-else class="panel">{{ t('capture.loading') }}</p>
  </main>
</template>

<style scoped>
main {
  max-width: 34rem;
  margin: 0 auto;
  padding: 1.5rem 1.25rem 4rem;
  display: grid;
  gap: var(--gap-section);
}

.eyebrow, .now { margin: 0; font-size: .8rem; color: var(--ink-3); }
.progress { margin: 0; font-variant-numeric: tabular-nums; color: var(--ink-2); }

.bar {
  height: .4rem;
  border-radius: var(--radius);
  background: var(--line);
  overflow: hidden;
}
.bar > i { display: block; height: 100%; background: var(--brand); transition: width .25s ease; }

h1 { margin: 0; font-size: 1.4rem; line-height: 1.25; }
.intro { margin: 0; color: var(--ink-2); }
.working { margin: 0; color: var(--ink-2); font-variant-numeric: tabular-nums; }

/* The input is the camera; the button is what the customer sees. */
.camera { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }

.areas { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--gap); }

.area {
  display: grid;
  gap: .45rem;
  border-top: 1px solid var(--line);
  padding-top: var(--gap);
}

/* The whole row is the control: on a phone held one-handed, a small chevron is a missed tap. */
.area-pick {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--gap);
  width: 100%;
  padding: .25rem 0;
  font: inherit;
  background: none;
  border: none;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.area-pick:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
.area[data-complete="true"] .label { color: var(--ink-3); }
.label { font-weight: 600; }
.count { color: var(--ink-3); font-variant-numeric: tabular-nums; font-size: .85rem; flex: none; }

.hint { margin: 0; color: var(--ink-2); font-size: .88rem; }

.frames { list-style: none; margin: .2rem 0 0; padding: 0; display: grid; gap: .4rem; }

.frameRow {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: .5rem;
  align-items: center;
  font-size: .95rem;
  color: var(--ink-2);
}
.frameRow[data-taken="true"] .frameName { color: var(--ink-3); }
.frameRow[data-taken="true"] .frameName::before { content: "✓ "; color: var(--brand); }

.flag { grid-column: 1; font-size: .78rem; color: var(--ink-3); }
.rejected { grid-column: 1 / -1; }

/* Secondary to the area choice above it, and there are up to five in a row. */
.shoot.btn.primary {
  min-height: 2.4rem;
  padding: 0 1rem;
  font-size: .92rem;
  font-weight: 600;
}

.retake {
  font: inherit;
  font-size: .85rem;
  cursor: pointer;
  background: none;
  border: none;
  padding: 0;
  color: var(--brand);
  text-decoration: underline;
}
.retake:disabled { color: var(--ink-3); cursor: not-allowed; }

.refused {
  display: grid;
  gap: var(--gap);
  border: 1px solid var(--danger);
  border-radius: var(--radius);
  padding: var(--gap-loose);
  background: var(--surface);
}

.refused img {
  display: block;
  width: 100%;
  max-height: 16rem;
  object-fit: contain;
  border-radius: var(--radius);
  background: var(--line);
}

.panel { margin: 0; color: var(--ink-2); }
.err { margin: 0; color: var(--danger); font-size: .9rem; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 3rem;
  padding: 0 1.4rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 1.05rem;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
}
.btn.primary { border-color: var(--brand); background: var(--brand); color: var(--brand-ink); }
.btn.primary:hover { background: var(--brand-hover); }
.btn.primary:disabled {
  border-color: var(--line-strong);
  background: var(--line);
  color: var(--ink-3);
  cursor: not-allowed;
}
.btn.outline { border-color: var(--brand); background: var(--surface); color: var(--brand); }
.btn.outline:hover { background: var(--brand-soft); }
</style>
