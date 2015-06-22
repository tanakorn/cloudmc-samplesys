package edu.uchicago.cs.ucare.samc.util;

import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;

public abstract class SpecVerifier {
    
    public ModelCheckingServerAbstract modelCheckingServer;

    public abstract boolean verify();
    public abstract String verificationDetail();

}
