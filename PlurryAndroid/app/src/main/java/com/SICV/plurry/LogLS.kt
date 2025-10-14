import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.content.Context

object LogLS {
    private fun getCallerInfo(): String {
        val stackTrace = Thread.currentThread().stackTrace
        // 0: getThreadStackTrace, 1: getCallerInfo, 2: d/e/w, 3: actual caller
        val caller = stackTrace[4]
        val className = caller.className.substringAfterLast('.')
        val methodName = caller.methodName
        return "[$className : $methodName]"
    }

    fun d(message: String) {
        Log.d("LogLS", "${getCallerInfo()} $message")
    }

    fun e(message: String) {
        Log.e("LogLS", "${getCallerInfo()} $message")
    }

    fun w(message: String) {
        Log.w("LogLS", "${getCallerInfo()} $message")
    }

    fun t(context: Context, message:String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}