<script setup lang="ts">
/**
 * Stage 3: the number, the code, and the handover (workflow §3.1, BOYA-45).
 *
 * §3.1 puts this last and says why: "Baştan numara isteyen sistemler ziyaretçinin yarısını kaybeder.
 * Bu noktada müşteri zaten 8 dakika emek harcamıştır, bırakmaz." The landing page has been promising
 * the same thing from the first screen — "telefon numaranız yalnızca hazırlanan teklifi
 * görüntüleyeceğiniz aşamada istenir" — so this screen is where that promise is kept, and it should
 * feel like the last small thing rather than a form.
 *
 * Two steps and one page. Splitting them across routes would put a back button between the customer
 * and a code that expires in five minutes.
 *
 * Verifying is not the end: §3's arrow into ANALYZING needs the photographs *and* the phone, so this
 * screen submits as well, and the promise it gets back is what the waiting screen shows.
 *
 * Not here: §3.1's "doğrulama adımı, fotoğraf yüklemesi arka planda devam ederken gösterilir" — this
 * screen is reached after the uploads finish, because they are still synchronous. That overlap is
 * BOYA-44's, and it is the whole reason §3.1 wanted it.
 */
const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.verify.title') }) })

const id = String(route.query.talep ?? '')

/** The same rule the server applies, so the answer is immediate; the server still decides. */
const TURKISH_MOBILE = /^(?:\+?90|0)?5\d{9}$/

const step = ref<'phone' | 'code'>('phone')

const phone = ref('')
const code = ref('')
const busy = ref(false)
const error = ref('')

const captureLink = computed(() => `/cekim/oda?talep=${encodeURIComponent(id)}`)

/**
 * When §11's limit lifts, as a clock the customer can watch.
 *
 * A static "1 dakika sonra tekrar deneyin" stops being true the moment it is rendered, and leaves
 * somebody pressing a button to find out whether the minute has passed. The server sends the number of
 * seconds precisely so this can be shown — and it is read rather than assumed, because §11's three
 * limits are a minute, an hour and a day, and the daily one told as "1 dakika" would be a lie.
 */
const retryAt = ref<number | null>(null)
const tick = ref(Date.now())
let ticking: ReturnType<typeof setInterval> | null = null

const secondsLeft = computed(() =>
  (retryAt.value === null ? 0 : secondsUntil(retryAt.value, tick.value)))
const waiting = computed(() => secondsLeft.value > 0)
const countdown = computed(() => retryWording(secondsLeft.value))

function keepTicking() {
  tick.value = Date.now()
  if (ticking === null) {
    ticking = setInterval(() => (tick.value = Date.now()), 250)
  }
}

/**
 * Re-read the clock whenever the page comes back.
 *
 * Browsers throttle timers in a hidden tab, so the interval above all but stops the moment the
 * customer leaves — and on this screen leaving is not an edge case, it is the instruction: they are
 * switching to their messages to fetch the code. Coming back to a countdown frozen where they left it
 * would show a code as alive that died while they were reading it.
 *
 * Nothing is derived from the interval itself; everything is computed against absolute instants, so
 * one refreshed reading here is the whole repair.
 */
function resync() {
  tick.value = Date.now()
}

function holdFor(seconds: number) {
  retryAt.value = Date.now() + seconds * 1000
  keepTicking()
}

/**
 * The code's own clock (§3.1).
 *
 * A code that quietly stops working is indistinguishable, on screen, from a code typed wrong — and the
 * customer answers both the same way, by retyping the digits they already have. The expiry is the
 * server's, sent back with the send; nothing here knows how long a code lives.
 */
const codeExpiresAt = ref<number | null>(null)
const codeLifetime = ref(0)

const codeSecondsLeft = computed(() =>
  (codeExpiresAt.value === null ? 0 : secondsUntil(codeExpiresAt.value, tick.value)))
const codeAlive = computed(() => codeExpiresAt.value !== null && codeSecondsLeft.value > 0)
const codeClock = computed(() => clockText(codeSecondsLeft.value))
const codeLeftFraction = computed(() =>
  remainingFraction(codeLifetime.value, codeSecondsLeft.value))

/** The ring is a circle drawn backwards: the dash gap grows as the code's life shrinks. */
const RING = 2 * Math.PI * 20
const ringOffset = computed(() => RING * (1 - codeLeftFraction.value))

function startCodeClock(expiresAt: string) {
  const ends = Date.parse(expiresAt)
  if (Number.isNaN(ends)) {
    codeExpiresAt.value = null
    return
  }
  codeExpiresAt.value = ends
  codeLifetime.value = Math.max(1, (ends - Date.now()) / 1000)
  keepTicking()
}

onMounted(() => {
  document.addEventListener('visibilitychange', resync)
  window.addEventListener('focus', resync)
})

onBeforeUnmount(() => {
  if (ticking !== null) {
    clearInterval(ticking)
  }
  document.removeEventListener('visibilitychange', resync)
  window.removeEventListener('focus', resync)
})

async function sendCode() {
  // Two reasons not to send, and the customer sees only one of them: the code in their hand still
  // works, or §11 refused the last attempt. The first is the ordinary case now that a code lives
  // exactly as long as the send window — asking again while one is alive would invalidate the message
  // that is very likely arriving at that moment.
  if (busy.value || waiting.value || codeAlive.value) {
    return
  }
  if (!TURKISH_MOBILE.test(phone.value.replace(/\s/g, ''))) {
    error.value = t('verify.invalidPhone')
    return
  }

  busy.value = true
  error.value = ''
  try {
    const { data, response, error: problem } = await api.POST('/api/otp/send',
      { body: { phone: phone.value } })
    if (response.status === 429) {
      // §11's limits. How long comes from the response, not from a guess: the same status covers a
      // minute, an hour and a day, and telling somebody "1 dakika" for the daily limit is a lie the
      // screen would repeat every time they came back.
      const seconds = (problem as { retryAfterSeconds?: number } | undefined)?.retryAfterSeconds
      error.value = t('verify.rateLimited')
      holdFor(typeof seconds === 'number' && seconds > 0 ? seconds : 60)
      return
    }
    if (!response.ok) {
      error.value = t('verify.sendFailed')
      return
    }
    code.value = ''
    step.value = 'code'
    if (data?.expiresAt) {
      startCodeClock(data.expiresAt)
    }
  }
  catch {
    error.value = t('verify.sendFailed')
  }
  finally {
    busy.value = false
  }
}

async function verifyCode() {
  if (busy.value) {
    return
  }
  if (!/^\d{6}$/.test(code.value.trim())) {
    error.value = t('verify.invalidCode')
    return
  }
  if (codeExpiresAt.value !== null && !codeAlive.value) {
    // The server would refuse it anyway, with a sentence that does not say why. The clock already
    // knows, so it says so without spending a round trip to be told.
    error.value = t('verify.expired')
    return
  }

  busy.value = true
  error.value = ''
  try {
    const verified = await api.POST('/api/otp/verify', { body: { code: code.value.trim() } })
    if (verified.response.status === 423) {
      // Locked: no number of further attempts will help, so the screen offers a new code instead of
      // another try. A single "wrong code" here would leave somebody retyping digits at a dead code.
      error.value = t('verify.locked')
      return
    }
    if (!verified.response.ok) {
      error.value = t('verify.wrongCode')
      return
    }

    await handOver()
  }
  catch {
    error.value = t('verify.wrongCode')
  }
  finally {
    busy.value = false
  }
}

/** §3's arrow into ANALYZING, and the promise that comes back with it. */
async function handOver() {
  const { data, response } = await api.POST('/api/quote-requests/{id}/submit', {
    params: { path: { id } },
  })
  if (response.status === 409) {
    // The photographs are not all in. Nothing to retry here: the customer goes back and finishes.
    error.value = t('verify.captureIncomplete')
    return
  }
  if (!response.ok || !data) {
    error.value = t('verify.submitFailed')
    return
  }

  await navigateTo(
    `/cekim/bekleme?talep=${encodeURIComponent(id)}&yanit=${encodeURIComponent(data.respondBy)}`)
}
</script>

<template>
  <main>
    <template v-if="!id">
      <p class="panel">{{ t('verify.incomplete') }}</p>
      <NuxtLink class="btn outline" to="/teklif-al">{{ t('verify.goToForm') }}</NuxtLink>
    </template>

    <template v-else>
      <p class="eyebrow">{{ t('verify.eyebrow') }}</p>

      <template v-if="step === 'phone'">
        <h1>{{ t('verify.title') }}</h1>
        <p class="intro">{{ t('verify.intro') }}</p>

        <label class="field">
          <span>{{ t('verify.phone') }}</span>
          <input
            v-model="phone"
            class="phone"
            type="tel"
            inputmode="tel"
            autocomplete="tel"
            :placeholder="t('verify.phonePlaceholder')"
            :disabled="busy"
            @keyup.enter="sendCode"
          >
        </label>
        <p class="why">{{ t('verify.why') }}</p>

        <p v-if="error" class="err" role="alert">
          {{ error }}
          <span v-if="waiting" class="countdown">
            {{ t(`verify.retry.${countdown.key}`, countdown.values) }}
          </span>
          <span v-else-if="retryAt !== null" class="countdown">{{ t('verify.retry.now') }}</span>
        </p>

        <button class="btn primary send" type="button" :disabled="busy || waiting" @click="sendCode">
          {{ busy ? t('verify.sending') : t('verify.send') }}
        </button>
      </template>

      <template v-else>
        <h1>{{ t('verify.codeTitle') }}</h1>
        <p class="intro">{{ t('verify.codeSent', { phone }) }}</p>

        <div v-if="codeExpiresAt !== null" class="life" :data-expired="!codeAlive">
          <svg class="dial" viewBox="0 0 48 48" role="img"
               :aria-label="codeAlive ? t('verify.expiresIn', { clock: codeClock }) : t('verify.expired')">
            <circle class="track" cx="24" cy="24" r="20" />
            <!--
              Drawn from twelve o'clock and swept clockwise, like a clock face rather than a bar.
              At zero it closes into a full red ring rather than emptying to nothing: an empty grey
              circle is indistinguishable from a dial that never started, and it left the expired
              colour with no stroke to colour.
            -->
            <circle
              class="sweep"
              cx="24"
              cy="24"
              r="20"
              :stroke-dasharray="RING"
              :stroke-dashoffset="codeAlive ? ringOffset : 0"
            />
          </svg>
          <p class="lifeText">
            <span v-if="codeAlive">{{ t('verify.expiresIn', { clock: codeClock }) }}</span>
            <span v-else>{{ t('verify.expired') }}</span>
          </p>
        </div>

        <label class="field">
          <span>{{ t('verify.code') }}</span>
          <input
            v-model="code"
            class="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            :placeholder="t('verify.codePlaceholder')"
            :disabled="busy"
            @keyup.enter="verifyCode"
          >
        </label>

        <p v-if="error" class="err" role="alert">{{ error }}</p>

        <button
          class="btn primary confirm"
          type="button"
          :disabled="busy || (codeExpiresAt !== null && !codeAlive)"
          @click="verifyCode"
        >
          {{ busy ? t('verify.verifying') : t('verify.verify') }}
        </button>

        <div class="secondary">
          <button
            class="link resend"
            type="button"
            :disabled="busy || waiting || codeAlive"
            @click="sendCode"
          >
            {{ waiting ? t(`verify.retry.${countdown.key}`, countdown.values) : t('verify.resend') }}
          </button>
          <button class="link change" type="button" :disabled="busy" @click="step = 'phone'">
            {{ t('verify.changePhone') }}
          </button>
        </div>
      </template>

      <NuxtLink class="link back" :to="captureLink">{{ t('verify.goToCapture') }}</NuxtLink>
    </template>
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

.eyebrow { margin: 0; font-size: .8rem; color: var(--ink-3); }
h1 { margin: 0; font-size: 1.4rem; line-height: 1.25; }
.intro { margin: 0; color: var(--ink-2); }
.why { margin: 0; font-size: .85rem; color: var(--ink-3); }

.field { display: grid; gap: .4rem; }
.field > span { font-size: .9rem; color: var(--ink-2); }

.field input {
  font: inherit;
  font-size: 1.1rem;
  min-height: 3rem;
  padding: 0 .8rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
}
.field input:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }

/* Six digits read as digits: spaced out, and never wrapped. */
.code { font-family: var(--mono); letter-spacing: .35em; }

.secondary { display: flex; flex-wrap: wrap; gap: var(--gap-loose); }

.link {
  font: inherit;
  font-size: .9rem;
  cursor: pointer;
  background: none;
  border: none;
  padding: 0;
  color: var(--brand);
  text-decoration: underline;
}
.link:disabled { color: var(--ink-3); cursor: not-allowed; }
.back { justify-self: start; }

.panel { margin: 0; color: var(--ink-2); }
.err { margin: 0; color: var(--danger); font-size: .9rem; }

/* Tabular figures so the digits do not jitter as they count down. */
.countdown { font-variant-numeric: tabular-nums; }

.life { display: flex; align-items: center; gap: var(--gap); }

.dial { width: 2.75rem; height: 2.75rem; flex: none; transform: rotate(-90deg); }

.dial .track { fill: none; stroke: var(--line); stroke-width: 4; }

.dial .sweep {
  fill: none;
  stroke: var(--brand);
  stroke-width: 4;
  stroke-linecap: round;
  /* Matches the 250 ms tick, so the hand glides between updates instead of stepping. */
  transition: stroke-dashoffset .25s linear, stroke .3s ease;
}

.life[data-expired="true"] .sweep { stroke: var(--danger); }

.lifeText { margin: 0; color: var(--ink-2); font-variant-numeric: tabular-nums; }
.life[data-expired="true"] .lifeText { color: var(--danger); }

/* A sweeping hand is decoration; the digits carry the meaning. */
@media (prefers-reduced-motion: reduce) {
  .dial .sweep { transition: none; }
}

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
</style>
