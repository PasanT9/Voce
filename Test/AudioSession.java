import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.util.*;


public class AudioSession implements Runnable{

  boolean stopCapture = false;
  ByteArrayOutputStream byteArrayOutputStream;
  AudioFormat audioFormat;
  TargetDataLine targetDataLine;
  AudioInputStream audioInputStream;
  SourceDataLine sourceDataLine;
  byte tempBuffer[] = new byte[200];
  byte memBuffer[][] = new byte[16][200];
  Session peer;
  static boolean sFlag = false;
  static byte userId;
  static boolean fFlag = false;


  public AudioSession(Session peer) {
    this.peer = peer;
  }
  public AudioSession(){

  }
  public void run(){
        System.out.println("Here");
    Scanner read= new Scanner(System.in);

    while(true){
      System.out.print("Wish to Speak: ");
      String input = read.nextLine();
      if(input.equals("YES")){
        fFlag = true;
      }
      else if(input.equals("NO")){
        sFlag = false;
        fFlag = false;
      }
    }

  }

  private AudioFormat getAudioFormat() {
    float sampleRate = 16000.0F;
    int sampleSizeInBits = 16;
    int channels = 2;
    boolean signed = true;
    boolean bigEndian = true;
    return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
  }

  public void captureAudio() {
      System.out.println("On Capture Audio");
    try {
      Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();    //get available mixers
      System.out.println("Available mixers:");
      Mixer mixer = null;
      for (int cnt = 0; cnt < mixerInfo.length; cnt++) {
        System.out.println(cnt + " " + mixerInfo[cnt].getName());
        mixer = AudioSystem.getMixer(mixerInfo[cnt]);

        Line.Info[] lineInfos = mixer.getTargetLineInfo();
        if (lineInfos.length >= 1 && lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
          System.out.println(cnt + " Mic is supported!");
          break;
        }
      }

      audioFormat = getAudioFormat();     //get the audio format
      DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

      targetDataLine = (TargetDataLine) mixer.getLine(dataLineInfo);
      targetDataLine.open(audioFormat);
      targetDataLine.start();

      DataLine.Info dataLineInfo1 = new DataLine.Info(SourceDataLine.class, audioFormat);
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo1);
      sourceDataLine.open(audioFormat);
      sourceDataLine.start();

      //Setting the maximum volume
      FloatControl control = (FloatControl)sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
      control.setValue(control.getMaximum());

    } catch (LineUnavailableException e) {
      System.out.println(e);
      System.exit(0);
    }

  }
  public void capture() {
    getHashId();
    System.out.println("user: "+ userId);
    byteArrayOutputStream = new ByteArrayOutputStream();
    stopCapture = false;
    try {
      int seq = 0;
      Thread i = new Thread(new AudioSession());
      i.start();
      //Record non-stop
      while (!stopCapture) {

        //Read from mic and store in temp buffer
        targetDataLine.read(tempBuffer, 0, tempBuffer.length);  //capture sound into tempBuffer
        seq = seq%16;
        tempBuffer[199] = (byte)seq++;
        tempBuffer[198] = userId;
        DatagramPacket packet = new DatagramPacket(tempBuffer, tempBuffer.length, peer.ip, peer.port);
        if(fFlag){
          peer.socket.send(packet);
          sFlag = true;
          fFlag = false;
        }

        //Send whats in buffer to the server using sockets
        if(sFlag){
          peer.socket.send(packet);
        }
      }
      byteArrayOutputStream.close();
    } catch (IOException e) {
      System.out.println(e);
      System.exit(0);
    }
  }

  private void getHashId(){
    String ip="";
    try{
      ip = InetAddress.getLocalHost().toString();
    }
    catch(Exception ex){
      ex.printStackTrace();
    }
    int port = (int)(Math.random()*500);
    int id = 1;
    for(int i=0;i<ip.length();++i)
    {
      id *= ((ip.charAt(i)+100)%500);
    }
    id *= port;
    if(id<0){
      id *= (-1);
    }
    userId = (byte)(id%500);
  }
  public byte[][] initializeMemBuffer(){
    byte[][] buffer = new byte[16][500];
    for(int i=0;i<16;++i)
    {
      buffer[i] = null;
    }
    return buffer;
  }

  public void play() {
    byteArrayOutputStream = new ByteArrayOutputStream();
    stopCapture = false;
    HashMap<Integer, Client> clientList = new HashMap<Integer, Client>();

    try {
      byte[] buffer=new byte[200];
      int seqNum= 0;
      int packetLoss = 0;
      memBuffer = initializeMemBuffer();

      //Play non-stop
      while (!stopCapture) {

        DatagramPacket packet=new DatagramPacket(buffer, buffer.length);

        peer.socket0.receive(packet);
        /*String sourceIp = packet.getAddress().getHostName();
        int sourcePort = packet.getPort();

        int hashId = getHashId(sourceIp,sourcePort);
        Client client;

        if(clientList.containsKey(hashId)){
          client = clientList.get(hashId);
        }
        else{
          client = new Client();
          clientList.put(hashId,client);
        }

        int userId = client.userId;*/
        buffer = packet.getData();

        /*System.out.println(userId+": "+buffer[499]);
        sourceDataLine.write(buffer, 0, 200);   //playing audio available in tempBuffer*/

        //Packet re-arranging algorithm

        //------------------------------------------------------------------------------------------------------
        if (buffer[199] >= 0 && buffer[199] <= 15) {

          int currentPacket = buffer[199];
          int speaker = buffer[198];
          //System.out.println(speaker+" "+userId);
          if(speaker != userId){
            //System.out.println("Another Speaking");
            sFlag = false;
          }
          else if(speaker == userId){
            System.out.println("Speaking");
            sFlag = true;
            continue;
          }
          //System.out.println("Expected: "+seqNum+" "+"Arrived: "+currentPacket);
          if(currentPacket != seqNum) {
            //System.out.println("Not in Sequence");
            if(memBuffer[seqNum] == null) {
              //System.out.println("Not in Buffer");
              memBuffer[currentPacket] = Arrays.copyOf(buffer, 200);
              ++packetLoss;
              if(packetLoss > 3){
                packetLoss = 0;
                continue;
              }
              else{
                continue;
              }
            }
            else{
              //System.out.println("Exist in Buffer: "+seqNum);
              buffer = Arrays.copyOf(memBuffer[seqNum], 200);
              memBuffer[seqNum] = null;
              memBuffer[currentPacket] = Arrays.copyOf(buffer, 200);
            }
          }
          //------------------------------------------------------------------------------------------------------

          //Play data in temp buffer
          byteArrayOutputStream.write(buffer, 0, 200);
          //System.out.println("Playing("+speaker+"): "+buffer[199]);
          sourceDataLine.write(buffer, 0, 200);   //playing audio available in tempBuffer

          //--------------------------------------------------------------------------------------------------------
          ++seqNum;
          seqNum %= 16;
          if(seqNum == 0){
            memBuffer = initializeMemBuffer();
          }
        }
      }
      byteArrayOutputStream.close();
    } catch (IOException e) {
      System.out.println(e);
      System.exit(0);
    }
  }
}
