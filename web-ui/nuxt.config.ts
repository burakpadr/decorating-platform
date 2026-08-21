// https://nuxt.com/docs/api/configuration/nuxt-config
import { DISTRICTS } from './app/utils/districts'

export default defineNuxtConfig({
  // One stylesheet, loaded globally: the tokens every screen reads. Page styles stay scoped and
  // reference these — the panel's first version put hex values in each page and inverted itself the
  // moment the operator's phone was in dark mode.
  css: ['~/assets/css/tokens.css'],

  compatibilityDate: '2026-08-16',
  devtools: { enabled: true },

  modules: ['@nuxtjs/i18n', '@pinia/nuxt', '@vite-pwa/nuxt'],

  runtimeConfig: {
    public: {
      // The API is called directly from the browser. Do not proxy through Nuxt server routes —
      // it adds a hop, breaks presigned uploads, and hides CORS problems until production.
      //
      // Nuxt overrides this from NUXT_PUBLIC_API_BASE at runtime; the value here is the local
      // default only.
      apiBase: 'http://localhost:8080',

      // `user:password` for the operator realm, from NUXT_PUBLIC_OPERATOR_AUTH. Empty by default and
      // meant to stay empty outside local development: it is a stand-in for the login screen the
      // panel does not have yet, not an authentication mechanism.
      operatorAuth: '',
    },
  },

  /*
   * Route strategy (§10).
   *
   * Marketing and district pages are prerendered: traffic is the real bottleneck in a self-serve
   * funnel, and district-level local search converts best in this sector.
   *
   * The capture and operator flows gain nothing from SSR — they are client-only, which also keeps
   * the camera and upload-queue code off the server.
   */
  routeRules: {
    '/': { prerender: true },
    '/nasil-calisir': { prerender: true },
    '/**-boya-badana-fiyatlari': { prerender: true },

    '/teklif-al/**': { ssr: false },
    // The handoff link from an SMS or a QR code: it exchanges a token for a session, which is a
    // client-side errand and must never be prerendered.
    '/devam/**': { ssr: false },
    '/cekim/**': { ssr: false },
    '/teklifim/**': { ssr: false },
    // The operator app is kept out of the index by public/robots.txt, not by a route rule.
    '/op/**': { ssr: false },
  },

  /*
   * The language rule (§1): customer-facing copy is Turkish and lives in the i18n layer only, never
   * in enum values, column names or route params. Everything the customer reads comes from
   * i18n/locales/*.json; identifiers stay English (`district=KADIKOY`).
   *
   * Turkish-only for v1, so no URL prefix — /teklif-al, not /tr/teklif-al. The locale plumbing is
   * here anyway because retrofitting it after the flows are written means touching every component.
   */
  i18n: {
    defaultLocale: 'tr',
    strategy: 'no_prefix',
    locales: [{ code: 'tr', language: 'tr-TR', name: 'Türkçe', file: 'tr.json' }],
  },

  nitro: {
    prerender: {
      // A route rule cannot enumerate a dynamic segment, so the 39 district pages are listed
      // explicitly. crawlLinks is off: the client-only flows would otherwise be walked into.
      crawlLinks: false,
      routes: [
        '/',
        '/nasil-calisir',
        ...DISTRICTS.map((d) => `/${d.slug}-boya-badana-fiyatlari`),
      ],
    },
  },

  pwa: {
    registerType: 'autoUpdate',
    // The manifest is build-time metadata, not runtime copy, so it cannot read the i18n layer.
    // Keep these two strings in step with brand.name / brand.tagline in i18n/locales/tr.json.
    manifest: {
      name: 'Boya Teklifi',
      short_name: 'Boya Teklifi',
      lang: 'tr',
      description: 'Eve gelmeden boya badana teklifi alın.',
      theme_color: '#ffffff',
      background_color: '#ffffff',
      display: 'standalone',
      start_url: '/',
    },
    workbox: {
      // The capture flow must survive a lift ride with no signal; the upload queue retries.
      navigateFallback: undefined,
      globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
    },
    devOptions: {
      enabled: false,
    },
  },

  app: {
    head: {
      htmlAttrs: { lang: 'tr' },
      meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1' }],
    },
  },

  typescript: {
    strict: true,
  },
})
