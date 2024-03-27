import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File

fun main() {
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val logFile = File("log.txt")
  for (i in 1..100) {
    val currentDT = LocalDateTime.now();
    val formattedDT = currentDT.format(formatter)
    val msg = "$formattedDT This is log number $i\n"
    println(msg)
    logFile.appendText(msg)
    Thread.sleep(500)// 500 ms
  }
}
