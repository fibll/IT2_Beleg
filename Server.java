/* ------------------
 Server
 usage: java Server [RTSP listening port]
 ---------------------- */

import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.concurrent.TimeUnit;

public class Server extends JFrame implements ActionListener {

	// RTP variables:
	// ----------------
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP
								// packets
	DatagramPacket senddp; // UDP packet containing the video frames

	InetAddress ClientIPAddr; // Client IP address
	int RTP_dest_port = 0; // destination port for RTP packets (given by the
							// RTSP Client)

	// packetLoss stuff
	Random random = new Random();
	static double packetLoss = 0.1;
	static int sliderPosition = 10;
	
	// GUI:
	// ----------------
	JLabel label;
	JSlider slider = new JSlider(JSlider.HORIZONTAL,
            0, 100, sliderPosition);

	// Video variables:
	// ----------------
	int imagenb = 0; // image nb of the image currently transmitted
	VideoStream video; // VideoStream object used to access video frames
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	static int FRAME_PERIOD = 40; // Frame period of the video to stream, in ms
	static int VIDEO_LENGTH = 500; // length of the video in frames

	Timer timer; // timer used to send the images at the video frame rate
	byte[] buf; // buffer used to store the images to send to the client

	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	// rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
	final static int OPTIONS = 7;
	final static int DESCRIBE = 8;

	static int state; // RTSP Server state == INIT or READY or PLAY
	static int tmpState = 0;
	Socket RTSPsocket; // socket used to send/receive RTSP messages
	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file requested from the client
	static int RTSP_ID = 123456; // ID of the RTSP session
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";


	// FEC Variables
	// ----------------
	int fecValue = 10;
	FECpacket fecPacket = new FECpacket();
	static int FEC_TYPE = 127; // RTP payload type for FEC
	
	
	// --------------------------------
	// Constructor
	// --------------------------------
	public Server() {

		// init Frame
		super("Server");

		// init Timer
		timer = new Timer(FRAME_PERIOD, this);
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		// allocate memory for the sending buffer
		buf = new byte[15000];

		// Handler to close the main window
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				// stop the timer and exit
				timer.stop();
				System.exit(0);
			}
		});

		// GUI:
		label = new JLabel("Send frame #        ", JLabel.CENTER);
		getContentPane().add(label, BorderLayout.CENTER);
		getContentPane().add(slider);
		
		// handler
		slider.addChangeListener(new SliderListener());
		
		//Turn on labels at major tick marks.
		slider.setMajorTickSpacing(10);
		slider.setMinorTickSpacing(1);
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {

		FECpacket fecPacket = new FECpacket();

		byte[] arr1 = {0,1,0,1,0,1,0,1,0,1,0};
		byte[] arr2 = {1,0,1,0,1,1,1,0,1,0,1};

		fecPacket.printArray(arr1);

		// create a Server object
		Server theServer = new Server();

		// show GUI:
		theServer.pack();
		theServer.setVisible(true);

		// get RTSP socket port from the command line
		int RTSPport = Integer.parseInt(argv[0]);

		// Initiate TCP connection with the client for the RTSP session
		ServerSocket listenSocket = new ServerSocket(RTSPport);
		theServer.RTSPsocket = listenSocket.accept();
		listenSocket.close();

		// Get Client IP address
		theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

		// Initiate RTSPstate
		state = INIT;

		// Set input and output stream filters:
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(
				theServer.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
				theServer.RTSPsocket.getOutputStream()));

		// Wait for the SETUP message from the client
		int request_type;
		boolean done = false;
		while (!done) {
			request_type = theServer.parse_RTSP_request(); // blocking

			if (request_type == SETUP) {
				done = true;

				// update RTSP state
				state = READY;
				System.out.println("New RTSP state: READY");

				// Send response
				theServer.send_RTSP_response(request_type);

				// init the VideoStream object:
				theServer.video = new VideoStream(VideoFileName);

				// init RTP socket
				theServer.RTPsocket = new DatagramSocket();
			} else if (request_type == OPTIONS) {
				state = tmpState;

				// send back response
				theServer.send_RTSP_response(request_type);
			} else if (request_type == TEARDOWN) {
				// send back response
				theServer.send_RTSP_response(request_type);
				// stop timer
				theServer.timer.stop();
				
				System.exit(0);
			}
		}

		// loop to handle RTSP requests
		while (true) {
			// parse the request
			request_type = theServer.parse_RTSP_request(); // blocking

			if ((request_type == PLAY) && (state == READY)) {
					
				// send back response
				theServer.send_RTSP_response(request_type);
				
				// start timer
				theServer.timer.start();
				// update state
				state = PLAYING;
				System.out.println("New RTSP state: PLAYING");
			} else if ((request_type == PAUSE) && (state == PLAYING)) {
				// send back response
				theServer.send_RTSP_response(request_type);
				// stop timer
				theServer.timer.stop();
				// update state
				state = READY;
				System.out.println("New RTSP state: READY");
			} else if (request_type == DESCRIBE) {
				state = tmpState;

				// send back response
				theServer.send_RTSP_response(request_type);

			} else if (request_type == OPTIONS) {
				state = tmpState;

				// send back response
				theServer.send_RTSP_response(request_type);

			} else if (request_type == TEARDOWN) {
				// send back response
				theServer.send_RTSP_response(request_type);
				// stop timer
				theServer.timer.stop();
				// close sockets
				theServer.RTSPsocket.close();
				theServer.RTPsocket.close();

				System.exit(0);
			}
		}
	}

	// ------------------------
	// Handler for timer
	// ------------------------
	public void actionPerformed(ActionEvent e) {

		// if the current image nb is less than the length of the video
		if (imagenb < VIDEO_LENGTH) {
			// update current imagenb
			imagenb++;
			//System.out.println("Sequencenumber: " + imagenb);

			try {
				// get next frame to send from the video, as well as its size
				int image_length = video.getnextframe(buf);
				//System.out.println("Image_length: " + image_length + "\nbuf_length" + buf.length);


				// Builds an RTPpacket object containing the frame
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb,
						imagenb * FRAME_PERIOD, buf, image_length);

				// get to total length of the full rtp packet to send
				int packet_length = rtp_packet.getlength();

				// retrieve the packet bitstream and store it in an array of
				// bytes
				byte[] packet_bits = new byte[packet_length];



				// =================
				// calculate the new fec value
				fecPacket.setData(Arrays.copyOf(buf, image_length));
				// =================



				rtp_packet.getpacket(packet_bits);
				
				// simulate packet loss
				if (random.nextDouble() > packetLoss) {
				
					// send the packet as a DatagramPacket over the UDP socket
					senddp = new DatagramPacket(packet_bits, packet_length,
							ClientIPAddr, RTP_dest_port);
					RTPsocket.send(senddp);
					//System.out.println("Sequencenumber: " + imagenb);
		        }

				// System.out.println("Send frame #"+imagenb);
				// print the header bitstream
				rtp_packet.printheader();

				// update GUI
				label.setText("Send frame #" + imagenb);

				
				// =================
				if((imagenb % fecValue) == 0){
					
					// wait FRAME_PERIOD/10
					Thread.sleep(FRAME_PERIOD/5);

					// get fec value
					byte[] fecBuf = fecPacket.getData();

					//System.out.println("\n\nbuf_length" + fecBuf.length + "\n\n");					

					// prepare packet
					rtp_packet = new RTPpacket(FEC_TYPE, imagenb,
						imagenb * FRAME_PERIOD, fecBuf, fecBuf.length);

					packet_length = rtp_packet.getlength();
					packet_bits = new byte[packet_length];
					rtp_packet.getpacket(packet_bits);

					// send packet
					senddp = new DatagramPacket(packet_bits, packet_length,
						ClientIPAddr, RTP_dest_port);
						RTPsocket.send(senddp);

					buf = new byte[15000];
					fecPacket.clearData();
				}
				// =================
				

			} catch (Exception ex) {
				System.out.println("Exception caught: " + ex);
				System.exit(0);
			}

		} else {
			// if we have reached the end of the video file, stop the timer
			timer.stop();
		}
	}
	
	// handler for slider
	class SliderListener implements ChangeListener {
	    public void stateChanged(ChangeEvent e) {
	        JSlider source = (JSlider)e.getSource();
	        if (!source.getValueIsAdjusting()) {
	        	System.out.println("Slider changed!");
	            packetLoss = (double)source.getValue()/100;
	            System.out.println("" + packetLoss);
	        }    
	    }
	}


	// ------------------------------------
	// Parse RTSP Request
	// ------------------------------------
	private int parse_RTSP_request() {
		int request_type = -1;
		try {
			// parse request line and extract the request_type:
			String RequestLine = RTSPBufferedReader.readLine();
			// System.out.println("RTSP Server - Received from Client:");
			System.out.println(RequestLine);

			StringTokenizer tokens = new StringTokenizer(RequestLine);
			String request_type_string = tokens.nextToken();

			// convert to request_type structure:
			if ((new String(request_type_string)).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((new String(request_type_string)).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0) {
				request_type = DESCRIBE;
				tmpState = state;
			} else if ((new String(request_type_string)).compareTo("OPTIONS") == 0) {
				request_type = OPTIONS;
				tmpState = state;
			} else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;

			System.out.println(request_type);

			if (request_type == SETUP) {
				// extract VideoFileName from RequestLine
				VideoFileName = tokens.nextToken();
			}

			// parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println(SeqNumLine);
			tokens = new StringTokenizer(SeqNumLine);
			tokens.nextToken();
			RTSPSeqNb = Integer.parseInt(tokens.nextToken());

			if (!(request_type == OPTIONS || request_type == DESCRIBE)) {
				// get LastLine
				String LastLine = RTSPBufferedReader.readLine();
				System.out.println(LastLine);

				if (request_type == SETUP) {
					// extract RTP_dest_port from LastLine
					tokens = new StringTokenizer(LastLine);
					for (int i = 0; i < 3; i++)
						tokens.nextToken(); // skip unused stuff
					RTP_dest_port = Integer.parseInt(tokens.nextToken());
				}
			}
			// else LastLine will be the SessionId line ... do not check for
			// now.
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
		return (request_type);
	}

	// ------------------------------------
	// Send RTSP Response
	// ------------------------------------
	private void send_RTSP_response(int request_type) {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);

			if (request_type == OPTIONS) {
				RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
				RTSPBufferedWriter
						.write("Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE"
								+ CRLF);

			} else if(request_type == DESCRIBE) {
				RTSPBufferedWriter.write("Content-Base: " + VideoFileName + CRLF);
				RTSPBufferedWriter.write("Content-Type: application/sdp" + CRLF);
				RTSPBufferedWriter.write("Content-Length: " + VIDEO_LENGTH + CRLF);
					
			} else if(request_type == SETUP){
				RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
				RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);

				// new value "fecValue" for Setup
				RTSPBufferedWriter.write("" + fecValue + CRLF);
			
			} else {
				RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
				RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			}


			RTSPBufferedWriter.flush();
			System.out.println("RTSP Server - Sent response to Client.");
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
	}
}
