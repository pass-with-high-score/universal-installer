package app.pwhs.universalinstaller.presentation.install

import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence

class EmptySplitSequence : CloseableSequence<Apk> {
    override var isClosed: Boolean = false
        private set

    override fun iterator(): Iterator<Apk> = emptyList<Apk>().iterator()

    override fun close() {
        isClosed = true
    }
}
