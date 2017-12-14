/* ------------------
 Client
 usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
 ---------------------- */

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Client {

	// GUI
	// ----
	JFrame f = new JFrame("Client");
	JButton setupButton = new JButton("Setup");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton describeButton = new JButton("Describe");
	JButton optionsButton = new JButton("Options");
	JButton tearButton = new JButton("Teardown");
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;
	
	// info panel
	JPanel infoPanel = new JPanel();
	JLabel lostPacketLabel = new JLabel("Lost Packets");
	JLabel lostPacketProcentLabel = new JLabel("Lost Percentage");

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP
								// packets
	static int RTP_RCV_PORT = 25000; // port where the client will receive the
										// RTP packets

	Timer timer; // timer used to receive data from the UDP socket
	byte[] buf; // buffer used to store data received from the server

	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	final static int OPTIONS = 3;
	final static int DESCRIBE = 4;
	static int state; // RTSP state == INIT or READY or PLAYING
	Socket RTSPsocket; // socket used to send/receive RTSP messages
	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file to request to the server
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
	int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

	final static String CRLF = "\r\n";

	// Video constants:
	// ------------------
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

	static int lostPackages = 0;

	static int lastSequenceNumber = 0;
	static int timerCounter = 0;
	
	private static DecimalFormat df = new DecimalFormat(".00");
	

	// FEC Variables
	// ------------------
	int fecValue = 0;	// aka k

	// the array should have k-times of undefinded (yet) length of byte[]
	// And cause it got a delay of k-pictures it would be easy to have this array doubled
	//  byte[2][k][unknown(yet)]
	byte[][][] pictureBuffer;
	byte[] fecData;


	// --------------------------
	// Constructor
	// --------------------------
	public Client() {

		// build GUI
		// --------------------------

		// Frame
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		// Buttons
		buttonPanel.setLayout(new GridLayout(1, 0));
		buttonPanel.add(setupButton);
		buttonPanel.add(playButton);
		buttonPanel.add(pauseButton);
		buttonPanel.add(describeButton);
		buttonPanel.add(optionsButton);
		buttonPanel.add(tearButton);
		
		infoPanel.add(lostPacketLabel);
		infoPanel.add(lostPacketProcentLabel);
		
		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		describeButton.addActionListener(new describeButtonListener());
		optionsButton.addActionListener(new optionsButtonListener());
		tearButton.addActionListener(new tearButtonListener());

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(buttonPanel);
		
		mainPanel.add(infoPanel);
		
		iconLabel.setBounds(0, 0, 380, 280);
		buttonPanel.setBounds(0, 280, 380, 50);

		infoPanel.setBounds(0, 400, 400, 50);
		
		f.getContentPane().add(mainPanel, BorderLayout.CENTER);
		f.setSize(new Dimension(390, 370));
		f.setVisible(true);

		// init timer
		// --------------------------
		// time is half as long as the original one, to catch every fec packet
		timer = new Timer(10, new timerListener());
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		// allocate enough memory for the buffer used to receive data from the
		// server
		buf = new byte[15000];
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {
		// Create a Client object
		Client theClient = new Client();

		// get server RTSP port and IP address from the command line
		// ------------------
		int RTSP_server_port = Integer.parseInt(argv[1]);
		String ServerHost = argv[0];
		InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

		// get video filename to request:
		VideoFileName = argv[2];

		// Establish a TCP connection with the server to exchange RTSP messages
		// ------------------
		theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

		// Set input and output stream filters:
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(
				theClient.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
				theClient.RTSPsocket.getOutputStream()));

		// init RTSP state:
		state = INIT;
	}

	// ------------------------------------
	// Handler for buttons
	// ------------------------------------

	// .............
	// TO COMPLETE
	// .............

	// Handler for Setup button
	// -----------------------
	class setupButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Setup Button pressed !");

			if (state == INIT) {
				// Init non-blocking RTPsocket that will be used to receive data
				try {
					// construct a new DatagramSocket to receive RTP packets
					// from the server, on port RTP_RCV_PORT
					RTPsocket = new DatagramSocket(RTP_RCV_PORT);

					// set TimeOut value of the socket to 5msec.
					RTPsocket.setSoTimeout(5);

				} catch (SocketException se) {
					System.out.println("Socket exception: " + se);
					System.exit(0);
				}

				// init RTSP sequence number
				RTSPSeqNb = 1;

				// Send SETUP message to the server
				send_RTSP_request("SETUP");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print new state
					state = READY;
					System.out.println("New RTSP state: READY");
				}
			}// else if state != INIT then do nothing
		}
	}

	// Handler for Play button
	// -----------------------
	class playButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Play Button pressed !");

			if (state == READY) {
				// increase RTSP sequence number
				RTSPSeqNb++;

				// Send PLAY message to the server
				send_RTSP_request("PLAY");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					state = PLAYING;
					System.out.println("New RTSP state: PLAYING");

					// start the timer
					timer.start();
				}
			}// else if state != READY then do nothing
		}
	}

	// Handler for Pause button
	// -----------------------
	class pauseButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Pause Button pressed !");

			if (state == PLAYING) {
				// increase RTSP sequence number
				// ........

				// Send PAUSE message to the server
				send_RTSP_request("PAUSE");

				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP state and print out new state
					state = READY;
					System.out.println("New RTSP state: READY");

					// stop the timer
					timer.stop();
				}
			}
			// else if state != PLAYING then do nothing
		}
	}

	// Handler for Describe button
	// -----------------------
	class describeButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Describe Button pressed !");

			// save state
			int tmpState = state;

			// change RTSP state and print out new state
			state = DESCRIBE;
			System.out.println("New RTSP state: DESCRIBE");

			// increase RTSP sequence number
			RTSPSeqNb++;

			// Send options message to the server
			send_RTSP_request("DESCRIBE");

			System.out.println("\nsend Describe request\n");

			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// set state back
				state = tmpState;
			}
		}
	}

	// Handler for Options button
	// -----------------------
	class optionsButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Options Button pressed !");

			// save state
			int tmpState = state;

			// change RTSP state and print out new state
			state = OPTIONS;
			System.out.println("New RTSP state: OPTIONS");

			// increase RTSP sequence number
			RTSPSeqNb++;

			// Send options message to the server
			send_RTSP_request("OPTIONS");

			System.out.println("\nsend Option request\n");

			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// set state back
				state = tmpState;
			}
		}
	}

	// Handler for Teardown button
	// -----------------------
	class tearButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			System.out.println("Teardown Button pressed !");

			// increase RTSP sequence number
			RTSPSeqNb++;

			// Send TEARDOWN message to the server
			send_RTSP_request("TEARDOWN");

			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// change RTSP state and print out new state
				state = INIT;
				System.out.println("New RTSP state: INIT");

				// stop the timer
				timer.stop();

				// exit
				System.exit(0);
			}
		}
	}

	// ------------------------------------
	// Handler for timer
	// ------------------------------------

	class timerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			timerCounter++;
			
			// Construct a DatagramPacket to receive data from the UDP socket
			rcvdp = new DatagramPacket(buf, buf.length);

			try {
				// receive the DP from the socket:
				RTPsocket.receive(rcvdp);

				// create an RTPpacket object from the DP
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(),
						rcvdp.getLength());

				// catch rtp packets with an payload type of 127
				if(rtp_packet.getpayloadtype() == 127){
					System.out.println("\n\nReceived payload type 127");


					// if there is something to correct:
						// do the correction stuff


					// switch the currentBufferIndex


					// if all pictures where corrected remove k old pictures from buffer

					return;
				}

				// add lost packages
				lostPackages += rtp_packet.getsequencenumber() - lastSequenceNumber - 1;
				
				if(timerCounter >= 50){
					lostPacketLabel.setText("Lost Packages: " + lostPackages);
					lostPacketProcentLabel.setText("Lost Packages in percent: " + df.format(((double)lostPackages/(double)rtp_packet.getsequencenumber())*100) + "%");
					timerCounter = 0;
				}
				
				// update last sequence number
				lastSequenceNumber = rtp_packet.getsequencenumber();
						
				// print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum # "
						+ rtp_packet.getsequencenumber() + " TimeStamp "
						+ rtp_packet.gettimestamp() + " ms, of type "
						+ rtp_packet.getpayloadtype());

				// print header bitstream:
				rtp_packet.printheader();

				// ==================================
				// do this to get the payload into pictureBuffer[]
				// how to fix the problem with the length?
				// => pictureBuffer[circle-index][k-index][length]

				// get the payload bitstream from the RTPpacket object
				int payload_length = rtp_packet.getpayload_length();
				byte[] payload = new byte[payload_length];
				rtp_packet.getpayload(payload);

				// get an Image object from the payload bitstream
				Toolkit toolkit = Toolkit.getDefaultToolkit();

				// ==================================
				// FIX: create image with data of pictureBuffer[]
				Image image = toolkit.createImage(payload, 0, payload_length);

				// display the image as an ImageIcon object
				icon = new ImageIcon(image);
				iconLabel.setIcon(icon);

			} catch (InterruptedIOException iioe) {
				// System.out.println("Nothing to read");
			} catch (IOException ioe) {
				System.out.println("Exception caught: " + ioe);
			}

				// =========================================
				// done with picture now comes the fec
				/*
			try {					
				// receive the DP from the socket:
				RTPsocket.receive(rcvdp);
				
				// create an RTPpacket object from the DP
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(),
				rcvdp.getLength());

				// print important header fields of the RTP packet received:
				System.out.println("Got RTP(FEC) packet with SeqNum # "
				+ rtp_packet.getsequencenumber() + " TimeStamp "
				+ rtp_packet.gettimestamp() + " ms, of type "
				+ rtp_packet.getpayloadtype());
				// =========================================
			} catch (InterruptedIOException iioe) {
				 System.out.println("FEC not read!");
			} catch (IOException ioe) {
				System.out.println("Exception caught: " + ioe);
			}*/
		}
	}

	// ------------------------------------
	// Parse Server Response
	// ------------------------------------
	private int parse_server_response() {
		int reply_code = 0;
		
		try {
			// parse status line and extract the reply_code:
			String StatusLine = RTSPBufferedReader.readLine();
			// System.out.println("RTSP Client - Received from Server:");
			System.out.println(StatusLine);

			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); // skip over the RTSP version
			reply_code = Integer.parseInt(tokens.nextToken());

			// if reply code is OK get and print the 2 other lines
			if (reply_code == 200) {
				if(state == DESCRIBE){
			
					// if state == DESCRIBE gets more lines then usual planed
					for (int i = 0; i < 3; i++) {
						System.out.println(RTSPBufferedReader.readLine());
					}
				
				} else if(state == INIT) {
					
					String SeqNumLine = RTSPBufferedReader.readLine();
					System.out.println(SeqNumLine);

					// special for options
					String SessionLine = RTSPBufferedReader.readLine();
					System.out.println(SessionLine);

					// if state == INIT gets the Session Id from the SessionLine
					if (state == INIT) {
						System.out.println("\n STATE == INIT \n");

						tokens = new StringTokenizer(SessionLine);
						tokens.nextToken(); // skip over the Session:
						RTSPid = Integer.parseInt(tokens.nextToken());
					}

					// get fecValue from server
					fecValue = Integer.parseInt(RTSPBufferedReader.readLine());

					// init pictureBuffer
					pictureBuffer = new byte[2][fecValue][];		

				} else {

					String SeqNumLine = RTSPBufferedReader.readLine();
					System.out.println(SeqNumLine);

					// special for options
					String SessionLine = RTSPBufferedReader.readLine();
					System.out.println(SessionLine);

					// if state == INIT gets the Session Id from the SessionLine
					if (state == INIT) {
						System.out.println("\n STATE == INIT \n");

						tokens = new StringTokenizer(SessionLine);
						tokens.nextToken(); // skip over the Session:
						RTSPid = Integer.parseInt(tokens.nextToken());
					}
				}
			}

		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}

		return (reply_code);
	}

	// ------------------------------------
	// Send RTSP Request
	// ------------------------------------

	// .............
	// TO COMPLETE
	// .............

	private void send_RTSP_request(String request_type) {
		try {
			// Use the RTSPBufferedWriter to write to the RTSP socket

			// write the request line:
			RTSPBufferedWriter.write(request_type + " " + VideoFileName
					+ " RTSP/1.0\n");

			// write the CSeq line:
			RTSPBufferedWriter.write("CSeq: " + String.valueOf(RTSPSeqNb)
					+ "\n");

			// check if request_type is equal to "SETUP" and in this case write
			// the Transport: line advertising to the server the port used to
			// receive the RTP packets RTP_RCV_PORT
			if (request_type.equals("SETUP")) {
				// =============================================== incomplete
				RTSPBufferedWriter.write("Transport: "
						+ "RTP/UDP; client_port= "
						+ String.valueOf(RTP_RCV_PORT) + "\n");
			}
			// if request_type "OPTIONS"
			else if (request_type.equals("OPTIONS")) {
				;
			}
			// if request_type "OPTIONS"
			else if (request_type.equals("DESCRIBE")) {
				;
			}
			// otherwise, write the Session line from the RTSPid field
			else {
				// =============================================== incomplete
				RTSPBufferedWriter.write("Session: " + String.valueOf(RTSPid)
						+ "\n");
			}

			RTSPBufferedWriter.flush();
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
	}

}// end of Class Client

