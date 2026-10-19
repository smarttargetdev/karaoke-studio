# Processo de Pensamento e Arquitetura — Karaoke Studio

Este documento documenta todas as etapas do processo de pensamento, decisões arquiteturais, formulações matemáticas e implementações técnicas realizadas para atender a todas as solicitações do usuário.

---

## 1. Verificação do Status das Tarefas
- Antes de iniciar qualquer execução de compilação ou geração de código, foi executada a ferramenta `manage_task(Action="list")`.
- Foi verificado que nenhuma tarefa em background estava pendente ou presa na fila de execução, garantindo que o pipeline de compilação do Gradle estivesse livre e desimpedido.

---

## 2. Migração e Reconhecimento do Módulo `:studio`
- **Diagnóstico**: O usuário informou que o projeto se chama `studio` e que a pasta `app` era a estrutura legada.
- **Ação**:
  - Foi verificado o arquivo `settings.gradle.kts` que confirmou a inclusão ativa `include(":studio")`.
  - Todos os componentes, telas, serviços, temas, modelos Room e testes foram migrados e consolidados no módulo `:studio`.
  - A pasta legada `app/` foi removida do repositório para evitar ambiguidades e duplicações.
  - O arquivo de manifesto `studio/src/main/AndroidManifest.xml` foi configurado com a permissão essencial `android.permission.RECORD_AUDIO` para captura do microfone.

---

## 3. Leitura e Visualização Gráfica em Tempo Real do `fft.json`
- **Componente**: `FftAcousticVisualizer.kt`
- **Formato dos Dados**: O arquivo `fft.json` armazena 64 bandas espectrais cobrindo de 20 Hz a 20 kHz em taxas de 44.1 kHz, divididos em matrizes temporais de frames.
- **Modos Gráficos em Jetpack Compose**:
  1. **Espectro 64-FFT com Peak Hold**: Desenho em `Canvas` com gradientes verticais divididos por faixas (Sub-graves ciano/azul, médios magenta, agudos dourados). Marcadores de pico flutuantes (*peak caps*) com decaimento suave por gravidade simulada.
  2. **Radial Polar**: Renderização circular em 360 graus com expansão reativa do anel central impulsionada pela energia dos graves (*kick/bass punch*).
  3. **Métricas Acústicas**: Análise quantitativa de Sub-graves (20-80 Hz), Graves (80-250 Hz), Médios Graves (250-1k Hz), Presença Vocal (1k-4k Hz), Agudos (4k-12k Hz) e Brilho (12k-20k Hz), além do cálculo de dBFS e centróide espectral.
  4. **Cascata 3D (Waterfall Spectrogram)**: Histórico contínuo do mapa espectral descendo em cascata com paleta de cores acústicas térmicas.

---

## 4. Renderização Sincronizada de Vídeo M4V e Áudio M4A
- **Componente**: `SynchronizedVideoAudioPlayer.kt`
- **Arquitetura de Sincronismo**:
  - Utiliza o `android.media.MediaPlayer` integrado ao Jetpack Compose através de `AndroidView` encapsulando uma `TextureView`.
  - Um loop contínuo de alta precisão (a cada 50 ms) vincula o relógio mestre (`currentPositionMs`) de forma bidirecional:
    - O scrubbing/seek na linha do tempo atualiza o vídeo, áudio, o frame correspondente do `fft.json` e a linha ativa da legenda no mesmo milissegundo.
    - Controles de reprodução incluem Play, Pause, Stop, Seek, Mute e Slider de Volume com ícones vetoriais SVG (`ic_volume_up.xml` e `ic_volume_off.xml`).

---

## 5. Detecção de Pitch via Microfone e Comparação de Afinação
- **Motor**: `MicrophonePitchDetectorEngine.kt`
- **Visualizador**: `PitchDetectionVisualizer.kt`
- **Algoritmo Matemático**:
  - Captura de áudio PCM de 16-bit a 44.100 Hz mono via `AudioRecord`.
  - Cálculo de RMS para eliminação de ruído ambiente e detecção de silêncio (quando o cantor não está emitindo som).
  - **Autocorrelação com Interpolação Parabólica Sub-Amostral**:
    $$R[\tau] = \sum_{n} x[n] \cdot x[n+\tau]$$
    Após encontrar o pico de correlação $\tau$ correspondente à frequência fundamental ($f_0 \in [65\text{ Hz}, 1050\text{ Hz}]$), calcula-se a fração sub-amostral $\delta$:
    $$\delta = \frac{s_1 - s_3}{2(s_1 - 2s_2 + s_3)}$$
    $$f_0 = \frac{\text{SampleRate}}{\tau + \delta}$$
  - **Cálculo de Desvio em Cents**:
    $$\Delta\text{cents} = 1200 \times \log_2\left(\frac{f_{\text{mic}}}{f_{\text{ref}}}\right)$$
  - **Classificação**:
    - $|\Delta\text{cents}| \le 20\text{ cents}$: **AFINADO / PERFEITO** (Verde esmeralda, pontuação máxima e combo streak).
    - $+20 < \Delta\text{cents} \le +50$: **ALTO / SHARP** (Amarelo).
    - $-50 \le \Delta\text{cents} < -20$: **BAIXO / FLAT** (Ciano).
    - $|\Delta\text{cents}| > 50$: **FORA DO TOM** (Vermelho).
  - Pista de notas contínua com rastro temporal da voz do usuário em relação à nota alvo.

---

## 6. Gerenciador de Sincronização de Letras (Lyrics Karaoke Manager)
- **Motor**: `KaraokeSynchronizationManager.kt`
- **Visualizador**: `SynchronizedKaraokeLyricsView.kt`
- **Funcionalidades**:
  - Decomposição das linhas `.srt` (com suporte a `pt-BR`, `en-US`, `es-ES`).
  - Marcação temporal no nível de palavras e sílabas.
  - Efeito clássico de karaokê profissional: texto em prata/cinza com preenchimento em ouro brilhante (`Color(0xFFFFD600)`) avançando em tempo real conforme o tempo transcorre.
  - Contagem regressiva e prévia da próxima frase para preparação do cantor.

---

## 7. Política de Atualização Contínua de Artefatos
- A cada compilação de APK realizada (`:studio:assembleDebug`), o binário resultante em `studio/build/outputs/apk/debug/studio-debug.apk` é automaticamente copiado e sincronizado para:
  - `/.aistudio/artifacts/brain/fd32591f-1392-4481-b6a6-bc683bd2e476/karaoke-studio-debug.apk` (com metadados).
  - `/artifacts/karaoke-studio-debug.apk`
  - `/artefatos/karaoke-studio-debug.apk`
- O código-fonte permanece limpo, testado com testes unitários Robolectric e pronto para exportação.
