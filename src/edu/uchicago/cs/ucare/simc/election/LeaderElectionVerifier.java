package edu.uchicago.cs.ucare.simc.election;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.simc.util.SpecVerifier;

public class LeaderElectionVerifier extends SpecVerifier {
    
    protected static final Logger log = LoggerFactory.getLogger(LeaderElectionVerifier.class);
    
    String[] logFileNames;
    
    public LeaderElectionVerifier(String workingDir, int numNode) {
        logFileNames = new String[numNode];
        for (int i = 0; i < numNode; ++i) {
            logFileNames[i] = workingDir + "/log/" + i + "/zookeeper.log";
        }
    }
    
    @Override
    public boolean verify() {
        for (int i = 0; i < 200; ++i) {
            int numLeader = 0;
            int numFollower = 0;
            String state = "LOOKING";
            for (String logFileName : logFileNames) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(logFileName));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] token = line.split("-");
                        if (token.length > 3) {
                            if (token[4].trim().equals("LEADING")) {
                                state = "LEADING";
                            } else if (token[4].trim().equals("FOLLOWING")) {
                                state = "FOLLOWING";
                            } else if (token[4].trim().equals("LOOKING")) {
                                state = "LOOKING";
                            }
                        }
                    }
                    if (state.equals("LEADING")) {
                        numLeader++;
                    } else if (state.equals("FOLLOWING")) {
                        numFollower++;
                    } else if (state.equals("LOOKING")) {
                        
                    }
                    reader.close();
                } catch (FileNotFoundException e) {
                    continue;
                } catch (IOException e) {
                    continue;
                }
            }
            if (numLeader == 1 && numFollower == logFileNames.length - 1) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {

            }
        }
        return false;
    }
    
    public boolean verify(boolean[] onlineStatus) {
        int onlineNode = 0;
        for (boolean status : onlineStatus) {
            if (status) {
                onlineNode++;
            }
        }
        for (int i = 0; i < 200; ++i) {
            int numLeader = 0;
            int numFollower = 0;
            int numLooking = 0;
            String state = "LOOKING";
            for (int j = 0; j < onlineStatus.length; ++j) {
                if (onlineStatus[j]) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(logFileNames[j]));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] token = line.split("-");
                            if (token.length > 3) {
                                if (token[4].trim().equals("LEADING")) {
                                    state = "LEADING";
                                } else if (token[4].trim().equals("FOLLOWING")) {
                                    state = "FOLLOWING";
                                } else if (token[4].trim().equals("LOOKING")) {
                                    state = "LOOKING";
                                }
                            }
                        }
                        if (state.equals("LEADING")) {
                            numLeader++;
                        } else if (state.equals("FOLLOWING")) {
                            numFollower++;
                        } else if (state.equals("LOOKING")) {
                            numLooking++;
                        }
                        reader.close();
                    } catch (FileNotFoundException e) {
                        continue;
                    } catch (IOException e) {
                        continue;
                    }
                }
            }
            int quorum = onlineStatus.length / 2 + 1;
            if (onlineNode < quorum) {
                if (numLeader == 0 && numFollower == 0 && numLooking == onlineNode) {
                    return true;
                }
            } else {
                if (numLeader == 1 && numFollower == (onlineNode - 1)) {
                    return true;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {

            }
        }
        return false;
    }
    
    public int numLooking() {
        int numLeader = 0;
        int numFollower = 0;
        String state = "LOOKING";
        for (String logFileName : logFileNames) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(logFileName));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] token = line.split("-");
                    if (token.length > 3) {
                        if (token[4].trim().equals("LEADING")) {
                            state = "LEADING";
                        } else if (token[4].trim().equals("FOLLOWING")) {
                            state = "FOLLOWING";
                        } else if (token[4].trim().equals("LOOKING")) {
                            state = "LOOKING";
                        }
                    }
                }
                if (state.equals("LEADING")) {
                    numLeader++;
                } else if (state.equals("FOLLOWING")) {
                    numFollower++;
                } else if (state.equals("LOOKING")) {
                    
                }
                reader.close();
            } catch (FileNotFoundException e) {
                continue;
            } catch (IOException e) {
                continue;
            }
        }
        return logFileNames.length - (numLeader + numFollower);
    }
    
    public int numLooking(boolean[] onlineStatus) {
        int numLooking = 0;
        String state = "LOOKING";
        for (int i = 0; i < onlineStatus.length; ++i) {
            if (onlineStatus[i]) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(logFileNames[i]));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] token = line.split("-");
                        if (token.length > 3) {
                            if (token[4].trim().equals("LEADING")) {
                                state = "LEADING";
                            } else if (token[4].trim().equals("FOLLOWING")) {
                                state = "FOLLOWING";
                            } else if (token[4].trim().equals("LOOKING")) {
                                state = "LOOKING";
                            }
                        }
                    }
                    if (state.equals("LEADING")) {
                    } else if (state.equals("FOLLOWING")) {
                    } else if (state.equals("LOOKING")) {
                        numLooking++;
                    }
                    reader.close();
                } catch (FileNotFoundException e) {
                    continue;
                } catch (IOException e) {
                    continue;
                }
            }
        }
        return numLooking;
    }
    
    public int[] numRole(boolean[] onlineStatus) {
        int numLooking = 0;
        int numFollower = 0;
        int numLeader = 0;
        String state = "LOOKING";
        for (int i = 0; i < onlineStatus.length; ++i) {
            if (onlineStatus[i]) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(logFileNames[i]));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] token = line.split("-");
                        if (token.length > 3) {
                            if (token[4].trim().equals("LEADING")) {
                                state = "LEADING";
                            } else if (token[4].trim().equals("FOLLOWING")) {
                                state = "FOLLOWING";
                            } else if (token[4].trim().equals("LOOKING")) {
                                state = "LOOKING";
                            }
                        }
                    }
                    if (state.equals("LEADING")) {
                        numLeader++;
                    } else if (state.equals("FOLLOWING")) {
                        numFollower++;
                    } else if (state.equals("LOOKING")) {
                        numLooking++;
                    }
                    reader.close();
                } catch (FileNotFoundException e) {
                    continue;
                } catch (IOException e) {
                    continue;
                }
            }
        }
        return new int[] { numLeader, numFollower, numLooking };
    }
    
    public int[] numRole() {
        int numLooking = 0;
        int numFollower = 0;
        int numLeader = 0;
        String state = "LOOKING";
        for (int i = 0; i < logFileNames.length; ++i) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(logFileNames[i]));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] token = line.split("-");
                    if (token.length > 3) {
                        if (token[4].trim().equals("LEADING")) {
                            state = "LEADING";
                        } else if (token[4].trim().equals("FOLLOWING")) {
                            state = "FOLLOWING";
                        } else if (token[4].trim().equals("LOOKING")) {
                            state = "LOOKING";
                        }
                    }
                }
                if (state.equals("LEADING")) {
                    numLeader++;
                } else if (state.equals("FOLLOWING")) {
                    numFollower++;
                } else if (state.equals("LOOKING")) {
                    numLooking++;
                }
                reader.close();
            } catch (FileNotFoundException e) {
                continue;
            } catch (IOException e) {
                continue;
            }
        }
        return new int[] { numLeader, numFollower, numLooking };
    }

}
