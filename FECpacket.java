import java.util.Arrays;

public class FECpacket
{
    int FEC_group;       // FEC-Gruppengröße
     
    byte[] mediastack; // Puffer für Medienpakete
    byte[] fecstack;   // Puffer für FEC-Pakete
    byte[] tmp;
    int maxLength = 1;

    // copy an array into a bigger one but keep the zeros in the bigger one
	//System.arraycopy(source, 0, destination, 0, source.length);
    
    // SENDER --------------------------------------
    public FECpacket() {
        mediastack = new byte[maxLength];
        fecstack = new byte[maxLength];
        tmp = new byte[maxLength];
    }

    // RECEIVER ------------------------------------
    public FECpacket(int FEC_group) {
        ;
    }
        
    
    // ----------------------------------------------
    // *** SENDER *** 
    // ----------------------------------------------
    
    // speichert Nutzdaten zur FEC-Berechnung
    public void setdata( byte[] data, int data_length) {
        // new data longer then the data before?
        if(data_length > maxLength) {
            maxLength = data_length;

            // modify fecstack so it is long enough
            tmp = Arrays.copyOf(fecstack, fecstack.length);
            fecstack = new byte[maxLength];
            System.arraycopy(tmp, 0, fecstack, 0, tmp.length); 
        }

        // get new mediastack array big enough
        mediastack = new byte[maxLength];
        // now mediastack and fecstack should be the same size

        // copy an array into a bigger one but keep the zeros in the bigger one
        System.arraycopy(data, 0, mediastack, 0, data.length);

        // create new fec
        for(int i = 0; i < maxLength; i++) {
            fecstack[i] ^= mediastack[i];
        }
    }
    
    // holt fertiges FEC-Paket, Rückgabe: Paketlänge 
    public byte[] getdata() {
        return fecstack;
    }
    


    // ------------------------------------------------
    // *** RECEIVER *** 
    // ------------------------------------------------

    /*
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvdata( int nr, byte[] data) {
        ;
    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe    
    public void rcvfec( int nr, byte[] data) {
        ;
    }
    
    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)    
    public byte[] getjpeg( int nr) {
        
    }
    
    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        
    }
    */
}
