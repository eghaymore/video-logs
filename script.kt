import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*
import java.util.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main(args : Array<String>) {
  val FPS = 16
  val FPATH = "log.txt"
  val VIDFPATH = "media/2024-03-26 22-03-06.mkv" //"media/2024-03-26 12-37-20.mkv"
  val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val STARTDT_s = "2024-03-26 22:03:06" //"2024-03-26 12:37:20"
  val STARTDT = LocalDateTime.parse(STARTDT_s, FORMATTER)
  
  fun findFirstInstance(fPath: String, searchString: String): Int {
    var lineNumber = 0
    var flag = false
    var out = -1 // not found
    File(fPath).forEachLine { line ->
      lineNumber++
      if (!flag && line.contains(searchString)) {
        out = lineNumber;
	flag = true// this seems stupid
        return@forEachLine
      }
    }
    return out
  }

  val firstInst = findFirstInstance(FPATH, STARTDT_s)
  println("The first instance is on line: $firstInst")
  
  // Get Duration
  // The bash command is:
  // ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "path_to_your_video_file"

  val durationProcessBuilder = ProcessBuilder(
        "ffprobe",
        "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        "$VIDFPATH"
    )
 
  val durationProcess = durationProcessBuilder.redirectErrorStream(true).start()
  val reader = BufferedReader(InputStreamReader(durationProcess.inputStream))
  var line: String?
  val durationOutput = StringBuilder()

  while (reader.readLine().also { line = it } != null) {
    durationOutput.append(line).append("\n")
  }
  reader.close()
  durationProcess.waitFor()
  val duration = durationOutput.toString().trim().toFloat()
  println("Duration of the video: $duration seconds")
  val numFrames = floor(duration * FPS).toInt()
  println("There are $numFrames frames at $FPS fps")

  // Create log file with only relevant logs
  // The bash command using tail is:
  // tail -n +starting_line input.txt > output.txt
  val logCmd = "tail -n +$firstInst $FPATH > temp.txt"
  val logProcess = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", logCmd))
  logProcess.waitFor() 
  
  // Read relevant logs into a queue
  val logs: Queue<String> = LinkedList()
  val queueFile = File("temp.txt")
  queueFile.forEachLine { line ->
        logs.offer(line)
  }

  // show 5 logs at a time
  var displayLogs = mutableListOf<String>()
  repeat(5) { displayLogs.add(logs.poll().replace(":", "\\:")) }

  // Split video into frames
  // The bash command is:
  // ffmpeg -i "path_to_your_video" -vf "fps=16" input_frame_%04d.png
  val splitProcessBuilder = ProcessBuilder(
        "ffmpeg",
        "-i", "$VIDFPATH",
        "-vf", "fps=$FPS",
        "media/input_frame%04d.png"
    )
  val splitProcess = splitProcessBuilder.start()
  splitProcess.waitFor()

  //--------------------------- This is the slow part------------------------
  // Draw relevant log files on each frame
  // The bash command is:
  // ffmpeg -i input_frame_0001.png -vf "drawtext=text='Hello, World':x=10:y=10:fontsize=24:fontcolor=white, drawtext=text='Hello, World':x=10:y=40:fontsize=24:fontcolor=white" hello.png
  val UPDATE_RATE = 16 // check for new logs at each interval
  var CURRENTDT = STARTDT.plusSeconds(1) // keep track of time // experimenting with slightly ahead
  //var CURRENTDT_s = ""
  var dispIdx = 0
  for(i in 1..numFrames) { // replace with numFrames
          val frameID = String.format("%04d", i)  
	  val frameName = "input_frame$frameID.png"
	  if (i % UPDATE_RATE == 0) { // update 	  
	    CURRENTDT = CURRENTDT.plusSeconds(1) // update by number of seconds elapsed // (i/FPS).toLong())
	    //CURRENTDT_s = CURRENTDT.format(FORMATTER) // testing
	    var newLog:String? = logs.peek() //peek() replaced debug
	    while(newLog != null && (LocalDateTime.parse(newLog.substring(0,19), FORMATTER)  < CURRENTDT)) {
	        displayLogs.add(logs.poll().replace(":", "\\:")) // displayLogs.add(newLog.replace(":", "\\:")) replaced debug
	        dispIdx++
	        newLog = logs.peek()
	    }
	  }
	  
	  val drawProcessBuilder = ProcessBuilder(
	    "ffmpeg", "-i", "media/$frameName", "-vf",
	    "drawtext=text='${displayLogs[dispIdx]}':x=10:y=10:fontsize=20:fontcolor=white," +
	    "drawtext=text='${displayLogs[dispIdx+1]}':x=10:y=40:fontsize=20:fontcolor=white," + 
 	    "drawtext=text='${displayLogs[dispIdx+2]}':x=10:y=70:fontsize=20:fontcolor=white," +
 	    "drawtext=text='${displayLogs[dispIdx+3]}':x=10:y=100:fontsize=20:fontcolor=white," +
 	    "drawtext=text='${displayLogs[dispIdx+4]}':x=10:y=140:fontsize=20:fontcolor=white",
	    //"drawtext=text='${CURRENTDT_s}':x=10:y=140:fontsize=20:fontcolor=white",
 	    "media/new_frame_$frameID.png"	     
	  )  
	  val drawProcess = drawProcessBuilder.redirectErrorStream(true).start()
	  drawProcess.waitFor()
  }
  // Combine frames into video
  // The bash command is:
  // ffmpeg -framerate 16 -i new_frame_%04d.png -c:v libx264 -crf 23 -pix_fmt yuv420p output_video.mp4
  val combineProcessBuilder = ProcessBuilder(
        "ffmpeg", "-framerate", "$FPS", "-i", "media/new_frame_%04d.png", "-c:v", "libx264", "-crf", "23",
        "-pix_fmt", "yuv420p", "media/output_video.mp4"
    ) 
  val combineProcess = combineProcessBuilder.start()
  combineProcess.waitFor()
  // Clean up
  val cleanProcessBuilder = ProcessBuilder(
    "/bin/bash", "-c", "rm media/input_frame* && rm media/new_frame*"
  )
  val cleanProcess = cleanProcessBuilder.start()
  cleanProcess.waitFor()  

}
