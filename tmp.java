import java.util.Arrays;
import FECpacket.java;

public class tmp
{
    public static void main(String argv[]) throws Exception {

        fecPacket = new FECpacket();
        
        byte[] arr1 = {0,1,0,1,0,1,0,1,0,1,0};
        byte[] arr2 = {1,0,1,0,1,1,1,0,1,0,1};
        
        fecPacket.printArray(arr1);
    }
}