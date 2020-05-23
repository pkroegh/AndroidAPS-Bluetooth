package info.nightscout.androidaps.plugins.treatments.fragments;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsTempTargetFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    private RecyclerView recyclerView;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempTargetsViewHolder> {

        Intervals<TempTarget> tempTargetList;
        TempTarget currentlyActiveTarget;

        RecyclerViewAdapter(Intervals<TempTarget> TempTargetList) {
            this.tempTargetList = TempTargetList;
            currentlyActiveTarget = tempTargetList.getValueByInterval(System.currentTimeMillis());
        }

        @NonNull
        @Override
        public TempTargetsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_temptarget_item, viewGroup, false);
            return new TempTargetsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(TempTargetsViewHolder holder, int position) {
            String units = ProfileFunctions.getSystemUnits();
            TempTarget tempTarget = tempTargetList.getReversed(position);
            holder.ph.setVisibility(tempTarget.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(tempTarget._id) ? View.VISIBLE : View.GONE);
            if (!tempTarget.isEndingEvent()) {
                holder.date.setText(DateUtil.dateAndTimeString(tempTarget.date) + " - " + DateUtil.timeString(tempTarget.originalEnd()));
                holder.duration.setText(DecimalFormatter.to0Decimal(tempTarget.durationInMinutes) + " min");
                holder.low.setText(tempTarget.lowValueToUnitsToString(units));
                holder.high.setText(tempTarget.highValueToUnitsToString(units));
                holder.reason.setText(tempTarget.reason);
            } else {
                holder.date.setText(DateUtil.dateAndTimeString(tempTarget.date));
                holder.duration.setText(R.string.cancel);
                holder.low.setText("");
                holder.high.setText("");
                holder.reason.setText("");
                holder.reasonLabel.setText("");
                holder.reasonColon.setText("");
            }
            if (tempTarget.isInProgress() && tempTarget == currentlyActiveTarget) {
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            } else if (tempTarget.date > DateUtil.now()) {
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorScheduled));
            } else {
                holder.date.setTextColor(holder.reasonColon.getCurrentTextColor());
            }
            holder.remove.setTag(tempTarget);
        }

        @Override
        public int getItemCount() {
            return tempTargetList.size();
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        class TempTargetsViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView date;
            TextView duration;
            TextView low;
            TextView high;
            TextView reason;
            TextView reasonLabel;
            TextView reasonColon;
            TextView remove;
            TextView ph;
            TextView ns;

            TempTargetsViewHolder(View itemView) {
                super(itemView);
                cv = itemView.findViewById(R.id.temptargetrange_cardview);
                date = itemView.findViewById(R.id.temptargetrange_date);
                duration = itemView.findViewById(R.id.temptargetrange_duration);
                low = itemView.findViewById(R.id.temptargetrange_low);
                high = itemView.findViewById(R.id.temptargetrange_high);
                reason = itemView.findViewById(R.id.temptargetrange_reason);
                reasonLabel = itemView.findViewById(R.id.temptargetrange_reason_label);
                reasonColon = itemView.findViewById(R.id.temptargetrange_reason_colon);
                ph = itemView.findViewById(R.id.pump_sign);
                ns = itemView.findViewById(R.id.ns_sign);
                remove = itemView.findViewById(R.id.temptargetrange_remove);
                remove.setOnClickListener(v -> {
                    final TempTarget tempTarget = (TempTarget) v.getTag();
                    OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.removerecord),
                            MainApp.gs(R.string.careportal_temporarytarget) + ": " + tempTarget.friendlyDescription(ProfileFunctions.getSystemUnits()) +
                                    "\n" + DateUtil.dateAndTimeString(tempTarget.date),
                            (dialog, id) -> {
                                final String _id = tempTarget._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
                                MainApp.getDbHelper().delete(tempTarget);
                            }, null);
                });
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_temptarget_fragment, container, false);

        recyclerView = view.findViewById(R.id.temptargetrange_recyclerview);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTempTargetsFromHistory());
        recyclerView.setAdapter(adapter);

        Button refreshFromNS = view.findViewById(R.id.temptargetrange_refreshfromnightscout);
        refreshFromNS.setOnClickListener(v ->
                OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.refresheventsfromnightscout) + " ?", () -> {
                    MainApp.getDbHelper().resetTempTargets();
                    RxBus.INSTANCE.send(new EventNSClientRestart());
                }));

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, true);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTempTargetChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    private void updateGui() {
        recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTempTargetsFromHistory()), false);
    }
}
