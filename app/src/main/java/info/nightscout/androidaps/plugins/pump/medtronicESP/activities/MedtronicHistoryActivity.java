package info.nightscout.androidaps.plugins.pump.medtronicESP.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.util.DataUtils;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.db.MedtronicActionHistory;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRHistoryActivity;
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.ToastUtils;

/*
 *
 *
 */

public class MedtronicHistoryActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(L.PUMP);

    private Handler mHandler;

    static Profile profile = null;

    Button reloadButton;
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    static byte showingType = RecordTypes.RECORD_TYPE_ALARM;
    List<MedtronicActionHistory> historyList = new ArrayList<>();

    public static class TypeList {
        public byte type;
        String name;

        TypeList(byte type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public MedtronicHistoryActivity() {
        super();
        HandlerThread mHandlerThread = new HandlerThread(MedtronicHistoryActivity.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.medtronic_historyactivity);

        recyclerView = (RecyclerView) findViewById(R.id.medtronicESP_history_recyclerview);
        reloadButton = (Button) findViewById(R.id.medtronic_historyreload);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(historyList);
        recyclerView.setAdapter(adapter);

        reloadButton.setOnClickListener(v -> {
            clearCardView();
            loadDataFromDB();
        });
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {
        List<MedtronicActionHistory> historyList;

        RecyclerViewAdapter(List<MedtronicActionHistory> historyList) {
            this.historyList = historyList;
        }

        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.medtronic_history_item, viewGroup, false);
            return new HistoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            MedtronicActionHistory record = historyList.get(position);
            holder.time.setText(DateUtil.dateAndTimeString(record.recordTime));
            holder.command.setText(record.command);
            if (record.isConfirmed) {
                holder.status.setText(R.string.medtronicESP_history_send);
            } else if (record.isSend) {
                holder.status.setText(R.string.medtronicESP_history_confirmed);
            } else {
                holder.status.setText("");
            }
            holder.note.setText(record.note);
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView time;
            TextView command;
            TextView status;
            TextView note;

            HistoryViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.medtronic_history_cardview);
                time = (TextView) itemView.findViewById(R.id.medtronic_history_time);
                command = (TextView) itemView.findViewById(R.id.medtronic_history_command);
                status = (TextView) itemView.findViewById(R.id.medtronic_history_status);
                note = (TextView) itemView.findViewById(R.id.medtronic_history_note);
            }
        }
    }

    private void loadDataFromDB() {
        historyList = MainApp.getDbHelper().getMedtronicHistoryRecords();
        runOnUiThread(() -> recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false));
    }

    private void clearCardView() {
        historyList = new ArrayList<>();
        runOnUiThread(() -> recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false));
    }
}