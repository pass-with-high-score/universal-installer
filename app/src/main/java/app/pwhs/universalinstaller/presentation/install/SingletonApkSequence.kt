package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence
import kotlin.concurrent.Volatile

class SingletonApkSequence(private val uri: Uri, context: Context) : CloseableSequence<Apk> {

    @Volatile
    override var isClosed: Boolean = false
        private set

    private val applicationContext = context.applicationContext
    private val cancellationSignal = CancellationSignal()

    override fun iterator(): Iterator<Apk> {
        return object : Iterator<Apk> {

            private val apk = Apk.fromUri(uri, applicationContext, cancellationSignal)
            private var isYielded = false

            override fun hasNext(): Boolean {
                return apk != null && !isYielded
            }

            override fun next(): Apk {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                isYielded = true
                return apk!!
            }
        }
    }

    override fun close() {
        isClosed = true
        cancellationSignal.cancel()
    }
}