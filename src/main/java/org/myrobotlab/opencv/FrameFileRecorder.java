package org.myrobotlab.opencv;

import java.io.File;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.myrobotlab.service.OpenCV;

public class FrameFileRecorder extends FrameRecorder {

  int frameIndex = 0;
  String rootDir = "OpenCV";
  String framesDir = null;
  long timestamp;

  public FrameFileRecorder(String cacheDir) {
    rootDir = cacheDir;
  }

  @Override
  public void start() throws Exception {
    timestamp = System.currentTimeMillis();
    framesDir = rootDir + File.separator + timestamp;
    File dir = new File(framesDir);
    dir.mkdirs();    
    if (format == null) {
      format = "png";
    }
  }

  @Override
  public void stop() throws Exception {
  }

  @Override
  public void record(Frame frame) throws Exception {  
    String filename = String.format(framesDir + File.separator + "%08d.%s", frameIndex, format);
    OpenCV.saveToFile(filename, OpenCV.toImage(frame));
    ++frameIndex;
  }

  @Override
  public void release() throws Exception {   
  }
  
  
  

}