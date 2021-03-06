package io.agora.scene.pklive;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcChannelEventHandler;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcChannel;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.models.ChannelMediaOptions;
import io.agora.rtc.video.VideoCanvas;
import io.agora.scene.pklive.databinding.PkLiveAudienceDetailActivityBinding;
import io.agora.uiwidget.function.GiftAnimPlayDialog;
import io.agora.uiwidget.function.GiftGridDialog;
import io.agora.uiwidget.function.LiveRoomMessageListView;
import io.agora.uiwidget.function.TextInputDialog;
import io.agora.uiwidget.utils.StatusBarUtil;

public class AudienceDetailActivity extends AppCompatActivity {

    private RtcEngine rtcEngine;
    private final RoomManager roomManager = RoomManager.getInstance();

    private PkLiveAudienceDetailActivityBinding mBinding;
    private RoomManager.RoomInfo roomInfo;
    private LiveRoomMessageListView.LiveRoomMessageAdapter<RoomManager.MessageInfo> mMessageAdapter;
    private final RoomManager.DataCallback<String> roomDeleteCallback = new RoomManager.DataCallback<String>() {
        @Override
        public void onObtained(String data) {
            if(data.equals(roomInfo.roomId)){
                runOnUiThread(() -> showRoomEndDialog());
            }
        }
    };
    private final RoomManager.DataCallback<RoomManager.GiftInfo> giftInfoDataCallback = new RoomManager.DataCallback<RoomManager.GiftInfo>() {
        @Override
        public void onObtained(RoomManager.GiftInfo data) {
            runOnUiThread(() -> {
                mMessageAdapter.addMessage(new RoomManager.MessageInfo(
                        "User-" + data.userId,
                        getString(R.string.live_room_message_gift_prefix),
                        data.getIconId()
                ));
                // ????????????
                new GiftAnimPlayDialog(AudienceDetailActivity.this)
                        .setAnimRes(data.getGifId())
                        .show();
            });
        }
    };
    private final RoomManager.DataCallback<RoomManager.MessageInfo> messageInfoDataCallback = msg -> runOnUiThread(()->{
        mMessageAdapter.addMessage(msg);
    });
    private final RoomManager.DataListCallback<RoomManager.UserInfo> userInfoDataListCallback = dataList -> runOnUiThread(()->{
        mBinding.hostUserView.setUserCount(dataList.size());
        mBinding.hostUserView.removeAllUserIcon();
        for (int i = 1; i <= 3; i++) {
            int index = dataList.size() - i;
            if(index >= 0){
                RoomManager.UserInfo userInfo = dataList.get(index);
                mBinding.hostUserView.addUserIcon(userInfo.getAvatarResId(), userInfo.userName);
            }
        }
    });
    private RtcChannel pkChannel;
    private final RoomManager.DataCallback<RoomManager.PKInfoModel> pkInfoModelDataCallback = data -> runOnUiThread(() -> {
        if(data == null){
            return;
        }
        if (data.status == RoomManager.PKApplyInfoStatus.accept) {
            // ??????PK
            int pkUid = new Random(System.currentTimeMillis()).nextInt(10000) + 200000;
            String pkChannelId = data.roomId;

            pkChannel = rtcEngine.createRtcChannel(pkChannelId);
            pkChannel.setRtcChannelEventHandler(new IRtcChannelEventHandler() {
                @Override
                public void onUserJoined(RtcChannel rtcChannel, int uid, int elapsed) {
                    super.onUserJoined(rtcChannel, uid, elapsed);
                    runOnUiThread(() -> {
                        mBinding.pkVideoContainer.setVisibility(View.VISIBLE);
                        mBinding.ivPkIcon.setVisibility(View.VISIBLE);

                        SurfaceView videoView = RtcEngine.CreateRendererView(AudienceDetailActivity.this);
                        mBinding.pkVideoContainer.removeAllViews();
                        mBinding.pkVideoContainer.addView(videoView);
                        rtcEngine.setupRemoteVideo(new VideoCanvas(videoView, io.agora.rtc.Constants.RENDER_MODE_HIDDEN,pkChannelId, uid));
                    });
                }

            });


            pkChannel.setClientRole(io.agora.rtc.Constants.CLIENT_ROLE_AUDIENCE);
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.autoSubscribeAudio = true;
            options.autoSubscribeVideo = true;
            pkChannel.joinChannel(getString(R.string.rtc_app_token), "", pkUid, options);

        } else if (data.status == RoomManager.PKApplyInfoStatus.end) {
            // ??????PK
            mBinding.pkVideoContainer.setVisibility(View.GONE);
            mBinding.pkVideoContainer.removeAllViews();
            mBinding.ivPkIcon.setVisibility(View.GONE);
            if(pkChannel != null){
                pkChannel.leaveChannel();
                pkChannel = null;
            }
        }
    });


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = PkLiveAudienceDetailActivityBinding.inflate(LayoutInflater.from(this));
        setContentView(mBinding.getRoot());
        StatusBarUtil.hideStatusBar(getWindow(), false);
        roomInfo = (RoomManager.RoomInfo) getIntent().getSerializableExtra("roomInfo");

        // ????????????
        mBinding.hostNameView.setName(roomInfo.roomName + "(" + roomInfo.roomId + ")");
        mBinding.hostNameView.setIcon(roomInfo.getAndroidBgId());

        // ???????????????
        mBinding.bottomView.setFun1Visible(true)
                .setFun1ImageResource(R.drawable.live_bottom_btn_gift)
                .setFun1ClickListener(v -> showGiftGridDialog());
        mBinding.bottomView.setFun2Visible(false);
        mBinding.bottomView.setupInputText(true, v -> showTextInputDialog());
        mBinding.bottomView.setupCloseBtn(true, v -> finish());
        mBinding.bottomView.setupMoreBtn(false, null);

        // ????????????
        mMessageAdapter = new LiveRoomMessageListView.LiveRoomMessageAdapter<RoomManager.MessageInfo>() {
            @Override
            protected void onItemUpdate(LiveRoomMessageListView.MessageListViewHolder holder, RoomManager.MessageInfo item, int position) {
                holder.setupMessage(item.userName, item.content, item.giftIcon);
            }
        };
        mBinding.messageList.setAdapter(mMessageAdapter);

        initRtcEngine();
        initRoomManager();
    }

    private void initRoomManager() {
        roomManager.joinRoom(roomInfo.roomId, () -> {
            roomManager.subscribeGiftReceiveEvent(roomInfo.roomId, giftInfoDataCallback);
            roomManager.subscribePKInfoEvent(roomInfo.roomId, pkInfoModelDataCallback);
            roomManager.subscriptRoomEvent(roomInfo.roomId, null, roomDeleteCallback);
            roomManager.subscribeMessageReceiveEvent(roomInfo.roomId, messageInfoDataCallback);
            roomManager.subscribeUserListChangeEvent(roomInfo.roomId, userInfoDataListCallback);
            roomManager.getRoomUserList(roomInfo.roomId, userInfoDataListCallback);
            roomManager.getPkInfo(roomInfo.roomId, pkInfoModelDataCallback);
        });
    }

    private void initRtcEngine() {
        try {
            rtcEngine = RtcEngine.create(this, getString(R.string.rtc_app_id), new IRtcEngineEventHandler() {
                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
                    super.onJoinChannelSuccess(channel, uid, elapsed);
                    runOnUiThread(() -> mMessageAdapter.addMessage(new RoomManager.MessageInfo("User-" + uid + "", getString(R.string.live_room_message_user_join_suffix))));
                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    super.onUserJoined(uid, elapsed);
                    runOnUiThread(() -> {
                        SurfaceView videoView = RtcEngine.CreateRendererView(AudienceDetailActivity.this);
                        mBinding.localVideoContainer.removeAllViews();
                        mBinding.localVideoContainer.addView(videoView);
                        rtcEngine.setupRemoteVideo(new VideoCanvas(videoView, Constants.RENDER_MODE_HIDDEN, uid));

                        mMessageAdapter.addMessage(new RoomManager.MessageInfo("User-" + uid + "", getString(R.string.live_room_message_user_join_suffix)));
                    });
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    super.onUserOffline(uid, reason);
                    runOnUiThread(() -> mMessageAdapter.addMessage(new RoomManager.MessageInfo("User-" + uid + "", getString(R.string.live_room_message_user_left_suffix))));
                }
            });
            rtcEngine.enableAudio();
            rtcEngine.enableVideo();
            rtcEngine.setChannelProfile(io.agora.rtc.Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);

            rtcEngine.setClientRole(io.agora.rtc.Constants.CLIENT_ROLE_AUDIENCE);
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.autoSubscribeVideo = true;
            options.autoSubscribeAudio = true;
            rtcEngine.joinChannel(getString(R.string.rtc_app_token), roomInfo.roomId, "", Integer.parseInt(RoomManager.getCacheUserId()), options);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showRoomEndDialog(){
        if(isDestroyed()){
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.common_tip)
                .setMessage(R.string.common_tip_room_closed)
                .setCancelable(false)
                .setPositiveButton(R.string.common_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void showGiftGridDialog() {
        new GiftGridDialog(this)
                .setOnGiftSendClickListener((dialog, item, position) -> {
                    dialog.dismiss();
                    RoomManager.GiftInfo giftInfo = new RoomManager.GiftInfo();
                    giftInfo.setIconNameById(item.icon_res);
                    giftInfo.setGifNameById(item.anim_res);
                    giftInfo.title = getString(item.name_res);
                    giftInfo.coin = item.coin_point;
                    giftInfo.userId = RoomManager.getCacheUserId();
                    roomManager.sendGift(roomInfo.roomId, giftInfo);
                })
                .show();
    }

    private void showTextInputDialog() {
        new TextInputDialog(this)
                .setOnSendClickListener((v, text) -> {
                    RoomManager.MessageInfo message = new RoomManager.MessageInfo(roomManager.getLocalUserInfo().userName, text);
                    roomManager.sendMessage(roomInfo.roomId, message);
                })
                .show();
    }

    @Override
    public void finish() {
        roomManager.leaveRoom(roomInfo);
        if(pkChannel != null){
            pkChannel.leaveChannel();
            pkChannel = null;
        }
        rtcEngine.leaveChannel();
        RtcEngine.destroy();
        super.finish();
    }
}
