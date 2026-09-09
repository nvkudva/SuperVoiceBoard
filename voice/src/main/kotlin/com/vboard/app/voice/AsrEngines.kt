package com.vboard.app.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.vboard.app.models.ModelStore
import com.vboard.core.text.RecognizerCase

class FinalAsr(paths: ModelStore.SpeechModelPaths) {

    private val recognizer = OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioCapture.SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = paths.encoder,
                    decoder = paths.decoder,
                    joiner = paths.joiner,
                ),
                tokens = paths.tokens,
                numThreads = 4,
                provider = "cpu",
                modelType = "nemo_transducer",
            ),
        ),
    )

    /** Transcribes one complete utterance; blocking, call on a worker thread. */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AudioCapture.SAMPLE_RATE)
            recognizer.decode(stream)
            // A no-op for a model that already returns mixed case, which this
            // one does; it is here so a model swap cannot start shouting.
            RecognizerCase.normalize(recognizer.getResult(stream).text)
        } finally {
            runCatching { stream.release() }
        }
    }

    fun release() {
        runCatching { recognizer.release() }
    }
}
