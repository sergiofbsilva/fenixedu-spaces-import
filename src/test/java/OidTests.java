public class OidTests {

    private static String getNewSpaceId(String externalId) {
        final long oid = Long.parseLong(externalId);
        final int idInternal = (int) (oid & 0x0000FFFF);
        final long cid = getSpaceCID() << 32;
        return Long.toString(cid + (idInternal >> 32));
    }

    private static long getSpaceCID() {
        return 570;
    }

    public static void testOids() {
        System.out.println(getNewSpaceId("2448131360897"));
    }

    public static void main(String[] args) {
        testOids();

    }
}
