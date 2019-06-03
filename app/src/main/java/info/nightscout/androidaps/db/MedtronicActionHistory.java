package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_MEDTRONICHISTORY)
public class MedtronicActionHistory {

    @DatabaseField
    public String command;

    @DatabaseField
    public boolean isSend;

    @DatabaseField
    public boolean isConfirmed;

    @DatabaseField
    public String note;

    @DatabaseField
    public long recordTime;

    public MedtronicActionHistory() {}

    public MedtronicActionHistory(String command, long time, boolean isFake) {
        this.recordTime = time;
        this.command = command;
        if (isFake) {
            this.isSend = true;
            this.isConfirmed = true;
            this.note = "Faking connection";
        } else {
            this.isSend = false;
            this.isConfirmed = false;
        }
    }

    public void setCommandSend() {
        this.isSend = true;
    }

    public boolean isCommandSend() {
        return this.isSend;
    }

    public void setCommandConfirmed() {
        this.isConfirmed = true;
    }

    public boolean isCommandConfirmed() {
        return this.isConfirmed;
    }
}
