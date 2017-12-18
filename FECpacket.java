import java.util.Arrays;

public class FECpacket
{
    int FEC_group;       // FEC-Gruppengröße
     
    byte[] mediastack; // Puffer für Medienpakete
    byte[] fecstack;   // Puffer für FEC-Pakete
    byte[] tmp;
    int maxLength = 1;
    int correctedPictures = 0;

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
        this.FEC_group = FEC_group;
    }
        
    
    // ----------------------------------------------
    // *** SENDER *** 
    // ----------------------------------------------
    
    // speichert Nutzdaten zur FEC-Berechnung
    public void setData(byte[] data) {
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
        System.out.println("Array: ");
        for(int i = 0; i < data.length; i++) {
            System.out.print("" + data[i] + " ");
        }
        System.out.println("\n");
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
    */
    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)    
    public byte[] getJpeg(int index, byte[][] buffer, byte[] fecEndValue) {
        byte[] fec_tmp = new byte[15000];

        // calc fec values up to index
        // for
        for(int i = 0; i < index; i++){
            setData(buffer[i]);
            printArray(buffer[i]);
            printArray(fecstack);
        }

        // System.out.println("\n\nFEC Before:");
        // printArray(fecstack);

        fec_tmp = Arrays.copyOf(fecstack, fecstack.length);

        // calc fec value from end to index
        fecstack = Arrays.copyOf(fecEndValue, fecEndValue.length);

        for(int i = buffer.length - 1; i > index; i--){
            // calculate backwards
            // set fecstack to fecEndValue

            setData(buffer[i]);
        }

        // System.out.println("\n\nFEC After:");
        // printArray(fecstack);


        // calc picture data (index) with the two fec values
            // guarantee that the packages are the same size
            if(fec_tmp.length > fecstack.length){
                tmp = Arrays.copyOf(fecstack, fecstack.length);
                fecstack = new byte[fec_tmp.length];
               	System.arraycopy(tmp, 0, fecstack, 0, tmp.length);
            }
            else{
                tmp = Arrays.copyOf(fec_tmp, fec_tmp.length);
                fec_tmp = new byte[fecstack.length];
               	System.arraycopy(tmp, 0, fec_tmp, 0, tmp.length);
            }

            // XOR
            for(int i = 0; i < maxLength; i++) {
                fecstack[i] ^= fec_tmp[i];
            }

        correctedPictures++;

        return fecstack;
    }
    
    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected() {
        return correctedPictures;        
    }

    public static void main(String argv[]) throws Exception {
        
        FECpacket fecPacket = new FECpacket();
        
        byte[] arr1 = {1,1,1,1,1};         
        byte[] arr2 = {0,1,0,1,1,0};
        byte[] arr3 = {1,1,0,1};
        byte[] arr4 = {1};

        fecPacket.setData(arr1);
        fecPacket.setData(arr2);
        fecPacket.setData(arr3);
        fecPacket.setData(arr4);
        byte[] fec = fecPacket.getData();
        //fecPacket.printArray(fec);

        fecPacket.clearData();

        byte[][] buffer = new byte[4][10];
        buffer[0] = Arrays.copyOf(arr1, arr1.length);
        buffer[1] = Arrays.copyOf(arr2, arr2.length);
        buffer[2] = Arrays.copyOf(arr3, arr3.length);
        buffer[3] = new byte[10];//Arrays.copyOf(arr4, arr4.length);

        byte[] missing = fecPacket.getJpeg(3, buffer, fec);
        System.out.println("\n\nMissing:");
        fecPacket.printArray(missing);
        

        /*
        fecPacket.printArray(arr1);
        fecPacket.printArray(arr2);

        fecPacket.setData(arr1);
        fecPacket.setData(arr2);
        fecPacket.setData(arr4);

        byte[] arr3 = fecPacket.getData();
        fecPacket.printArray(arr3);
        */
    }
}
