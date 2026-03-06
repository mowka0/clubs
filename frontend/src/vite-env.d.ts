/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MOCK_INIT_DATA: string
  readonly VITE_MOCK_START_PARAM: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
