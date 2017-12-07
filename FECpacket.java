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
    public void setData( byte[] data) {
        // new data longer then the data before?
        if(data.length > maxLength) {
            maxLength = data.length;

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

    public void printArray(byte[] data) {
        System.out.println("\n\nArray: ");
        for(int i = 0; i < data.length; i++) {
            System.out.print("" + data[i] + " ");
        }
    }
    
    // holt fertiges FEC-Paket, Rückgabe: Paketlänge 
    public byte[] getData() {
        return fecstack;
    }

    // clear fec array
    public void clearData() {
        fecstack = new byte[maxLength];
    }
    


    // ------------------------------------------------
    // *** RECEIVER *** 
    // ------------------------------------------------

    /*
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvData( int nr, byte[] data) {
        ;
    }

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe    
    public void rcvFec( int nr, byte[] data) {
        ;
    }
    
    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)    
    public byte[] getJpeg( int nr) {
        
    }
    
    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        
    }
    */

    public static void main(String argv[]) throws Exception {
        
        FECpacket fecPacket = new FECpacket();
                
        byte[] arr2 = {0,1,0,1,0,1,0,1,0,1,0};
        byte[] arr1 = {1,0,1,0,1,1,1,0,1,0,1,1,1,1,1,1}; 
        byte[] arr4 = {1};
                
        fecPacket.printArray(arr1);
        fecPacket.printArray(arr2);

        fecPacket.setData(arr1);
        fecPacket.setData(arr2);
        fecPacket.setData(arr4);

        byte[] arr3 = fecPacket.getData();
        fecPacket.printArray(arr3);
    }
}
