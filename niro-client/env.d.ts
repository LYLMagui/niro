/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BASE: string;
  readonly VITE_PORT: string;
  readonly VITE_BASE_API: string;
  readonly VITE_APP_URL: string;
  readonly VITE_APP_TITLE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
