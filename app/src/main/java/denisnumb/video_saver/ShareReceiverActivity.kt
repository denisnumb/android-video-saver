package denisnumb.video_saver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import denisnumb.video_saver.Constants.Companion.SHARED_URL

class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val link = extractLink(intent)

        if (link != null) {
            openMainActivity(link)
        }

        finish()
    }

    private fun extractLink(intent: Intent?): String? {
        if (intent == null)
            return null

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
    }

    private fun openMainActivity(url: String) {
        val i = Intent(this, MainActivity::class.java)
        i.putExtra(SHARED_URL, url)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
    }
}
