# Player Android

App Android 11+ feito com Kotlin e Jetpack Compose.

## Implementado
- Download de URLs HTTPS diretas autorizadas (MP4) pelo DownloadManager.
- Seleção de vídeos locais pelo seletor de documentos Android.
- Extração/conversão de áudio para M4A (AAC) com Media3 Transformer.
- Exportação para a pasta Música pelo MediaStore.
- Workflow GitHub Actions para APK debug.

## Limitações
- MP3 ainda não implementado: AAC/M4A não é MP3. Será necessário integrar um codificador MP3 adequado (por exemplo, LAME), considerando licença e distribuição.
- Não baixa vídeos de páginas YouTube nem contorna restrições de serviços.
- URLs devem apontar diretamente para arquivos de vídeo e permitir download.

## Build
No GitHub: Actions > Android APK > Run workflow. Baixe o artefato Player-debug-apk.
Ou execute `gradle :app:assembleDebug` com Android SDK e JDK 17.
