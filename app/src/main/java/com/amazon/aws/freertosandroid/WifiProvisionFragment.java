package com.amazon.aws.freertosandroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.amazon.aws.amazonfreertossdk.AmazonFreeRTOSManager;
import com.amazon.aws.amazonfreertossdk.NetworkConfigCallback;
import com.amazon.aws.amazonfreertossdk.networkconfig.DeleteNetworkReq;
import com.amazon.aws.amazonfreertossdk.networkconfig.DeleteNetworkResp;
import com.amazon.aws.amazonfreertossdk.networkconfig.EditNetworkReq;
import com.amazon.aws.amazonfreertossdk.networkconfig.EditNetworkResp;
import com.amazon.aws.amazonfreertossdk.networkconfig.ListNetworkReq;
import com.amazon.aws.amazonfreertossdk.networkconfig.ListNetworkResp;
import com.amazon.aws.amazonfreertossdk.networkconfig.SaveNetworkReq;
import com.amazon.aws.amazonfreertossdk.networkconfig.SaveNetworkResp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.support.v7.widget.helper.ItemTouchHelper.ACTION_STATE_SWIPE;
import static android.support.v7.widget.helper.ItemTouchHelper.DOWN;
import static android.support.v7.widget.helper.ItemTouchHelper.LEFT;
import static android.support.v7.widget.helper.ItemTouchHelper.UP;

public class WifiProvisionFragment extends Fragment {
    private static final String TAG = "WifiProvisioningFragment";
    private static final String DIALOG_TAG = "WiFiCredentialDialogTag";
    private static final int SAVED_NETWORK_RSSI = -100;
    private static final int REQUEST_CODE = 0;
    private RecyclerView mWifiInfoRecyclerView;
    private WifiInfoAdapter mWifiInfoAdapter;
    private List<WifiInfo> mWifiInfoList = new ArrayList<>();
    private HashMap<String, WifiInfo> mBssid2WifiInfoMap = new HashMap<>();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private AmazonFreeRTOSManager mAmazonFreeRTOSManager;

    private class WifiInfoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView mSsidTextView;
        private TextView mRssiTextView;
        private TextView mBssidTextView;
        private TextView mNetworkTypeTextView;
        private WifiInfo mWifiInfo;
        private Fragment mHostingFragment;

        public WifiInfoHolder(LayoutInflater inflater, ViewGroup parent, Fragment fragment) {
            super(inflater.inflate(R.layout.list_wifi_info, parent, false));
            mHostingFragment = fragment;
            mSsidTextView = (TextView) itemView.findViewById(R.id.ssid_name);
            mRssiTextView = (TextView) itemView.findViewById(R.id.rssi_value);
            mBssidTextView = (TextView) itemView.findViewById(R.id.bssid_name);
            mNetworkTypeTextView = (TextView) itemView.findViewById(R.id.network_type);
            itemView.setOnClickListener(this);
        }

        public void bind(WifiInfo wifiInfo) {
            mWifiInfo = wifiInfo;
            mSsidTextView.setText(mWifiInfo.getSsid());
            mRssiTextView.setText(getResources().getString(R.string.rssi_value, mWifiInfo.getRssi()));
            mBssidTextView.setText(bssidToString(mWifiInfo.getBssid()));
            mNetworkTypeTextView.setText(mWifiInfo.getNetworkTypeName());
            if (SAVED_NETWORK_RSSI == mWifiInfo.getRssi()) {
                int colorAccent = getResources().getColor(R.color.colorAccent, null);
                mSsidTextView.setTextColor(colorAccent);
                mBssidTextView.setTextColor(colorAccent);
                mRssiTextView.setTextColor(colorAccent);
                mNetworkTypeTextView.setTextColor(colorAccent);
            }
            if (mWifiInfo.isConnected()) {
                int colorPrimary = getResources().getColor(R.color.colorPrimary, null);
                mSsidTextView.setTextColor(colorPrimary);
                mBssidTextView.setTextColor(colorPrimary);
                mRssiTextView.setTextColor(colorPrimary);
                mNetworkTypeTextView.setTextColor(colorPrimary);
            }
        }

        @Override
        public void onClick(View v) {
            FragmentManager manager = getFragmentManager();
            WifiCredentialFragment dialog = WifiCredentialFragment.newInstance(
                    mWifiInfo.getSsid(), bssidToString(mWifiInfo.getBssid()));
            dialog.setTargetFragment(mHostingFragment, REQUEST_CODE);
            dialog.show(manager, DIALOG_TAG);
        }
    }

    private class WifiInfoAdapter extends RecyclerView.Adapter<WifiInfoHolder> {
        private List<WifiInfo> mWifiInfoList;
        private Fragment mHostingFragment;

        public WifiInfoAdapter(List<WifiInfo> wifiInfos, Fragment fragment) {
            mWifiInfoList = wifiInfos;
            mHostingFragment = fragment;
        }

        @Override
        public WifiInfoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            return new WifiInfoHolder(layoutInflater, parent, mHostingFragment);
        }

        @Override
        public void onBindViewHolder(WifiInfoHolder holder, int position) {
            WifiInfo wifiInfo = mWifiInfoList.get(position);
            holder.bind(wifiInfo);
        }

        @Override
        public int getItemCount() {
            return mWifiInfoList.size();
        }

        public void deleteItem(int position) {
            WifiInfo wifiInfo = mWifiInfoList.get(position);
            deleteNetwork(wifiInfo.getIndex());
            mWifiInfoList.remove(position);
            notifyItemRemoved(position);
        }

        public WifiInfo getItem(int position) {
            return mWifiInfoList.get(position);
        }

        public void moveItem(int oldPosition, int newPosition) {
            WifiInfo oldWifiInfo = mWifiInfoList.get(oldPosition);
            mWifiInfoList.remove(oldPosition);
            mWifiInfoList.add(newPosition, oldWifiInfo);
            notifyItemMoved(oldPosition, newPosition);
        }

        public void reorderItem(int fromIndex, int toIndex) {
            if (fromIndex != toIndex) {
                Log.d(TAG, "Reordering item: [" + fromIndex + "] -> [" + toIndex + "]");
                editNetwork(fromIndex, toIndex);
            }
        }
    }

    private class DragSwipeController extends ItemTouchHelper.SimpleCallback {

        private WifiInfoAdapter mAdapter;
        private Drawable mDeleteIcon;
        private final ColorDrawable mDeleteBackground;
        private boolean mMoving;
        private int mFromIndex, mToIndex;

        public DragSwipeController(WifiInfoAdapter adapter) {
            super(UP|DOWN, LEFT);
            mAdapter = adapter;
            mDeleteIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_delete);
            mDeleteBackground = new ColorDrawable(Color.RED);
            mMoving = false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            mAdapter.deleteItem(position);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                              RecyclerView.ViewHolder target) {
            if (!mMoving) {
                mFromIndex = viewHolder.getAdapterPosition();
                mMoving = true;
            }
            mAdapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (mMoving) {
                mToIndex = viewHolder.getAdapterPosition();
                mMoving = false;
                mAdapter.reorderItem(mFromIndex, mToIndex);
            }
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            WifiInfo wifiInfo = mAdapter.getItem(position);
            if (SAVED_NETWORK_RSSI != wifiInfo.getRssi()) {
                return 0;
            }
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {

            View itemView = viewHolder.itemView;
            if (actionState == ACTION_STATE_SWIPE) {
                float alpha = 1 - (Math.abs(dX) / recyclerView.getWidth());
                itemView.setAlpha(alpha);
            }

            int iconMargin = (itemView.getHeight() - mDeleteIcon.getIntrinsicHeight()) / 2;
            int iconTop = itemView.getTop() + (itemView.getHeight() - mDeleteIcon.getIntrinsicHeight()) / 2;
            int iconBottom = iconTop + mDeleteIcon.getIntrinsicHeight();

            int iconLeft = itemView.getRight() - iconMargin - mDeleteIcon.getIntrinsicWidth();
            int iconRight = itemView.getRight() - iconMargin;
            mDeleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

            mDeleteBackground.setBounds(itemView.getRight() + ((int) dX) ,
                    itemView.getTop(), itemView.getRight(), itemView.getBottom());

            mDeleteBackground.draw(c);
            mDeleteIcon.draw(c);
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wifi_provision, container, false);
        mWifiInfoRecyclerView = (RecyclerView) view.findViewById(R.id.wifi_recycler_view);
        mWifiInfoRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mWifiInfoAdapter = new WifiInfoAdapter(mWifiInfoList, this);
        mWifiInfoRecyclerView.setAdapter(mWifiInfoAdapter);

        DragSwipeController dragSwipeController = new DragSwipeController(mWifiInfoAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(dragSwipeController);
        itemTouchHelper.attachToRecyclerView(mWifiInfoRecyclerView);
        mAmazonFreeRTOSManager = AmazonFreeRTOSAgent.getAmazonFreeRTOSManager(getActivity());

        listNetworks();

        return view;
    }

    private NetworkConfigCallback mNetworkConfigCallback = new NetworkConfigCallback() {
        @Override
        public void onListNetworkResponse(final ListNetworkResp response) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    WifiInfo wifiInfo = new WifiInfo(response.getSsid(), response.getBssid(),
                            response.getRssi(), response.getSecurity(), response.getIndex(),
                            response.getConnected());
                    mWifiInfoList.add(wifiInfo);
                    mBssid2WifiInfoMap.put(bssidToString(wifiInfo.getBssid()), wifiInfo);
                    mWifiInfoAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onSaveNetworkResponse(final SaveNetworkResp response) {
            refreshUI();
        }

        @Override
        public void onDeleteNetworkResponse(final DeleteNetworkResp response) {
            refreshUI();
        }

        @Override
        public void onEditNetworkResponse(EditNetworkResp response) {
            refreshUI();
        }

        private void refreshUI() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    listNetworks();
                }
            });
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_CODE) {
            String pw = data.getStringExtra(WifiCredentialFragment.EXTRA_WIFI_PW);
            String bssid = data.getStringExtra(WifiCredentialFragment.EXTRA_WIFI_BSSID);
            saveNetwork(bssid, pw);
        }
    }

    private void saveNetwork(String bssid, String pw) {
        SaveNetworkReq saveNetworkReq = new SaveNetworkReq();
        WifiInfo wifiInfo = mBssid2WifiInfoMap.get(bssid);
        saveNetworkReq.ssid = wifiInfo.getSsid();
        saveNetworkReq.bssid = wifiInfo.getBssid();
        saveNetworkReq.psk = pw;
        saveNetworkReq.security = wifiInfo.getNetworkType();
        saveNetworkReq.index = wifiInfo.getIndex();
        mAmazonFreeRTOSManager.saveNetwork(saveNetworkReq, mNetworkConfigCallback);
    }

    private void listNetworks() {
        mWifiInfoList.clear();
        ListNetworkReq listNetworkReq = new ListNetworkReq();
        listNetworkReq.maxNetworks = 20;
        listNetworkReq.timeout = 5;
        mAmazonFreeRTOSManager.listNetworks(listNetworkReq, mNetworkConfigCallback);
    }

    private void deleteNetwork(int index) {
        DeleteNetworkReq deleteNetworkReq = new DeleteNetworkReq();
        deleteNetworkReq.index = index;
        mAmazonFreeRTOSManager.deleteNetwork(deleteNetworkReq, mNetworkConfigCallback);
    }

    private void editNetwork(int oldIndex, int newIndex) {
        EditNetworkReq editNetworkReq = new EditNetworkReq();
        editNetworkReq.index = oldIndex;
        editNetworkReq.newIndex = newIndex;
        mAmazonFreeRTOSManager.editNetwork(editNetworkReq, mNetworkConfigCallback);
    }

    private String bssidToString(byte[] bssid) {
        StringBuilder sb = new StringBuilder(18);
        for (byte b : bssid) {
            if (sb.length() > 0)
                sb.append(':');
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
