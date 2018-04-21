package firesoft.de.kalenderadapter.data;

public class CustomCalendar {

    //=======================================================
    //======================VARIABLEN========================
    //=======================================================

    /**
     * Enthält die ID des Kalenders
     */
    private int id;

    /**
     * Enthält den Namen der dem Benutzer angezeigt wird
     */
    private String displayName;

    /**
     * Enthält den Namen des Benutzeraccounts
     */
    private String accountName;

    /**
     * Enthält den Namen des Besitzers
     */
    private String ownerName;

    //=======================================================
    //=====================KONSTRUKTOR=======================
    //=======================================================

    /**
     * Erzeugt eine neue Instanz
     * @param id ID des Kalenders
     * @param displayName Anzuzeigender Name des Kalenders
     * @param accountName Name des Benutzeraccounts
     * @param ownerName Name des Benutzers
     */
    public CustomCalendar(int id, String displayName, String accountName, String ownerName) {
        this.id = id;
        this.displayName = displayName;
        this.accountName = accountName;
        this.ownerName = ownerName;
    }


    //=======================================================
    //=======================GETTER==========================
    //=======================================================

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getOwnerName() {
        return ownerName;
    }



}
