package edu.uchicago.cs.ucare.example.election;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderElectionMain {
	
    private static final Logger LOG = LoggerFactory.getLogger(LeaderElectionMain.class);
    
	public static final int LOOKING = 0;
	public static final int FOLLOWING = 1;
	public static final int LEADING = 2;
	
	public static String getRoleName(int role) {
		String name;
		switch (role) {
		case LeaderElectionMain.LOOKING:
			name = "looking";
			break;
		case LeaderElectionMain.FOLLOWING:
			name = "following";
			break;
		case LeaderElectionMain.LEADING:
			name = "leading";
			break;
		default:
			name = "unknown";
			break;
		}
		return name;
	}
	
	static int id;
	static int role;
	static int leader;
	
	static Map<Integer, InetSocketAddress> nodeMap;
	static Map<Integer, Sender> senderMap;
	static Processor processor;
	
	static Map<Integer, Integer> electionTable;
	
	public static void readConfig(String config) throws IOException {
		nodeMap = new HashMap<Integer, InetSocketAddress>();
		BufferedReader br = new BufferedReader(new FileReader(config));
		String line;
		while ((line = br.readLine()) != null) {
			String[] tokens = line.trim().split("=");
			assert tokens.length == 2;
			int nodeId = Integer.parseInt(tokens[0]);
			String[] inetSocketAddress = tokens[1].split(":");
			assert inetSocketAddress.length == 2;
			InetSocketAddress nodeAddress = new InetSocketAddress(inetSocketAddress[0], Integer.parseInt(inetSocketAddress[1]));
			nodeMap.put(nodeId, nodeAddress);
			LOG.info("node " + nodeId + " is " + nodeAddress);
		}
		LOG.info("Cluster size = " + nodeMap.size());
		br.close();
	}
	
	public static void work() throws IOException {
		senderMap = new HashMap<Integer, Sender>();
		InetSocketAddress myAddress = nodeMap.get(id);
		processor = new Processor();
		processor.start();
        final ServerSocket server = new ServerSocket(myAddress.getPort());
        Thread listeningThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
		            Socket connection;
					try {
						connection = server.accept();
                        DataInputStream dis = new DataInputStream(connection.getInputStream());
                        DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
                        int otherId = dis.readInt();
                        LOG.info("connection from " + otherId);
                        boolean isAllowed = otherId > id;
                        dos.writeBoolean(isAllowed);
                        dos.flush();
                        if (!isAllowed) {
                        	LOG.info("connection from " + otherId + " is not allowed");
                            connection.close();
                        } else {
                            Sender sender = new Sender(otherId, connection);
                            senderMap.put(otherId, sender);
                            sender.start();
                            Receiver receiver = new Receiver(otherId, connection);
                            receiver.start();
                        }
					} catch (IOException e) {
						// TODO Auto-generated catch block
						LOG.error("", e);
						break;
					}
				}
				try {
					server.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					LOG.error("", e);
				}
			}
        	
        });
        listeningThread.start();
        try {
			Thread.sleep(100);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			LOG.error("", e1);
		}
        // Sleep to make sure that every node is running now
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            LOG.error("", e1);
        }
        // TODO Auto-generated method stub
        for (Integer nodeId : nodeMap.keySet()) {
            if (nodeId != id) {
                InetSocketAddress address = nodeMap.get(nodeId);
                try {
                    LOG.info("Connecting to " + nodeId);
                    Socket connect = new Socket(address.getAddress(), address.getPort());
                    DataOutputStream dos = new DataOutputStream(connect.getOutputStream());
                    dos.writeInt(id);
                    dos.flush();
                    DataInputStream dis = new DataInputStream(connect.getInputStream());
                    boolean isAllowed = dis.readBoolean();
                    if (isAllowed) {
                        LOG.info("Connecting to " + nodeId + " is allowed");
                        Sender sender = new Sender(nodeId, connect);
                        senderMap.put(nodeId, sender);
                        sender.start();
                        Receiver receiver = new Receiver(nodeId, connect);
                        receiver.start();
                    } else {
                        LOG.info("Connecting to " + nodeId + " is not allowed");
                        connect.close();
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    LOG.error("", e);
                }
            }
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            LOG.error("", e);
        }
        LOG.info("First send all " + senderMap);
        processor.sendAll(getCurrentMessage());
	}
	
	static void updateState(int role,int leader) {
		LeaderElectionMain.role = role;
		LeaderElectionMain.leader = leader;
		electionTable.put(id, leader);
	}
	
	static boolean isBetterThanCurrentLeader(ElectionMessage msg) {
		return msg.leader > leader;
	}
	
	static int isFinished() {
		int totalNode = nodeMap.size();
		Map<Integer, Integer> count = new HashMap<Integer, Integer>();
		for (Integer electedLeader : electionTable.values()){
			count.put(electedLeader, count.containsKey(electedLeader) ? count.get(electedLeader) + 1 : 1);
		}
		LOG.info("Count table " + count);
		for (Integer electedLeader : count.keySet()) {
			int totalElect = count.get(electedLeader);
			if (totalElect > totalNode / 2) {
				return electedLeader;
			}
		}
		return -1;
	}
	
	static ElectionMessage getCurrentMessage() {
		return new ElectionMessage(id, role, leader);
	}
	
	public static void main(String[] args) throws IOException {

		if (args.length != 2) {
			System.err.println("usage: LeaderElectionMain <id> <config>");
			System.exit(1);
		}

		id = Integer.parseInt(args[0]);
		role = LOOKING;
		leader = id;
		
		LOG.info("Started:my id = " + id + " role = " + getRoleName(role) + " " + " leader = " + leader);
		
        electionTable = new HashMap<Integer, Integer>();
        electionTable.put(id, leader);

		readConfig(args[1]);
		work();
		
	}
	
	public static class Receiver extends Thread {
		
		int otherId;
		Socket connection;

		public Receiver(int otherId, Socket connection) {
			this.otherId = otherId;
			this.connection = connection;
		}
		
		void read(DataInputStream dis, byte[] buffer) throws IOException {
			int alreadyRead = 0;
            while (alreadyRead != ElectionMessage.SIZE) {
                alreadyRead = dis.read(buffer, alreadyRead, ElectionMessage.SIZE - alreadyRead);
            }
		}
		
		@Override
		public void run() {
			LOG.info("Start receiver for " + otherId);
			try {
				DataInputStream dis = new DataInputStream(connection.getInputStream());
                byte[] buffer = new byte[ElectionMessage.SIZE];
                while (!connection.isClosed()) {
                	LOG.info("Reading message for " + otherId);
                	read(dis, buffer);
                    ElectionMessage msg = new ElectionMessage(otherId, buffer);
                    LOG.info("Get message : " + msg.toString());
                    processor.process(msg);
                }
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG.error("", e);
			}
		}
		
	}
	
	public static class Sender extends Thread {
		
		int otherId;
		Socket connection;
		LinkedBlockingQueue<ElectionMessage> queue;
		OutputStream os;
		
		public Sender(int otherId, Socket connection) {
			this.otherId = otherId;
			this.connection = connection;
			try {
				os = connection.getOutputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG.error("", e);
			}
			queue = new LinkedBlockingQueue<ElectionMessage>();
		}
		
		public void send(ElectionMessage msg) {
			try {
				queue.put(msg);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				LOG.error("", e);
			}
		}
		
		public synchronized void write(ElectionMessage msg) {
            try {
				os.write(msg.toBytes());
                os.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG.error("", e);
			}
		}
		
		@Override
		public void run() {
			LOG.info("Start sender for " + otherId);
            while (!connection.isClosed()) {
                try {
                    ElectionMessage msg = queue.take();
                    LOG.info("Send message : " + msg.toString() + " to " + otherId);
                    write(msg);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    LOG.error("", e);
                }
            }
		}

		public int getOtherId() {
			return otherId;
		}

		public void setOtherId(int otherId) {
			this.otherId = otherId;
		}
		
	}
	
	public static class Processor extends Thread {
		
		LinkedBlockingQueue<ElectionMessage> queue;
		
		public Processor() {
			queue = new LinkedBlockingQueue<ElectionMessage>();
		}
		
		public void process(ElectionMessage msg) {
			queue.add(msg);
		}
		
		public void sendAll(ElectionMessage msg) {
			LOG.info("Sender map " + senderMap);
			for (Integer nodeId : senderMap.keySet()) {
				if (nodeId != id) {
					senderMap.get(nodeId).send(msg);
				}
			}
		}
		
		@Override
		public void run() {
			LOG.info("Start processor");
			ElectionMessage msg;
			while (true) {
				try {
					msg = queue.take();
					LOG.info("Process message : " + msg.toString());
                    electionTable.put(msg.getSender(), msg.getLeader());
					switch (role) {
					case LOOKING:
						switch (msg.getRole()) {
						case LOOKING:
							if (isBetterThanCurrentLeader(msg)) {
								LOG.info("Message " + msg + " is better");
								leader = msg.getLeader();
								electionTable.put(id, leader);
								int newLeader = isFinished();
								LOG.info("New leader = " + newLeader);
								if (newLeader != -1) {
									LOG.info("Finished election, leader = " + newLeader);
									if (newLeader == id) {
										role = LEADING;
									} else {
										role = FOLLOWING;
									}
								}
                                sendAll(getCurrentMessage());
							}
							break;
						case FOLLOWING:
						case LEADING:
							leader = msg.getLeader();
							electionTable.put(id, leader);
							int newLeader = isFinished();
                            LOG.info("Believe new leader = " + newLeader);
							if (newLeader != -1) {
								if (newLeader == id) {
									role = LEADING;
								} else {
									role = FOLLOWING;
								}
							}
                            sendAll(getCurrentMessage());
							break;
						}
						break;
					case FOLLOWING:
					case LEADING:
						switch (msg.getRole()) {
						case LOOKING:
							sendAll(getCurrentMessage());
							break;
						case FOLLOWING:
						case LEADING:
							// NOTE assume that conflict leader never happen
							break;
						}
						break;
					}
					LOG.info("Finished processing " + msg);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					LOG.error("", e);
				}
			}
		}
		
	}

}
