package com.hyphenate.chatuidemo.ui.chat;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMGroup;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chatuidemo.DemoHelper;
import com.hyphenate.chatuidemo.R;
import com.hyphenate.chatuidemo.ui.BaseActivity;
import com.hyphenate.chatuidemo.ui.call.VideoCallActivity;
import com.hyphenate.chatuidemo.ui.call.VoiceCallActivity;
import com.hyphenate.chatuidemo.ui.user.GroupDetailsActivity;
import com.hyphenate.chatuidemo.ui.user.UserEntity;
import com.hyphenate.chatuidemo.ui.widget.ChatInputView;
import com.hyphenate.chatuidemo.ui.widget.VoiceRecordDialog;
import com.hyphenate.chatuidemo.ui.widget.VoiceRecordView;
import com.hyphenate.chatuidemo.ui.widget.chatrow.ChatRowCall;
import com.hyphenate.easeui.EaseConstant;
import com.hyphenate.easeui.utils.EaseCommonUtils;
import com.hyphenate.easeui.widget.EaseMessageListView;
import com.hyphenate.easeui.widget.chatrow.EaseChatRow;
import com.hyphenate.easeui.widget.chatrow.EaseCustomChatRowProvider;
import com.hyphenate.util.PathUtil;
import java.io.File;
import java.util.List;

/**
 * Chat with someone in this activity
 */
public class ChatActivity extends BaseActivity {
    @BindView(R.id.input_view) ChatInputView mInputView;
    @BindView(R.id.message_list) EaseMessageListView mMessageListView;

    protected static final int REQUEST_CODE_MAP = 1;
    protected static final int REQUEST_CODE_CAMERA = 2;
    protected static final int REQUEST_CODE_LOCAL = 3;
    protected static final int REQUEST_CODE_SELECT_VIDEO = 11;
    protected static final int REQUEST_CODE_SELECT_FILE = 12;
    protected static final int REQUEST_CODE_GROUP_DETAIL = 13;
    protected static final int REQUEST_CODE_CONTEXT_MENU = 14;

    protected static final int MESSAGE_TYPE_RECV_CALL = 1;
    protected static final int MESSAGE_TYPE_SENT_CALL = 2;

    protected File cameraFile;

    /**
     * to chat user id or group id
     */
    protected String toChatUsername;

    /**
     * chat type, single chat or group chat
     */
    protected int chatType;

    protected EMConversation conversation;

    /**
     * load 20 messages at one time
     */
    protected int pageSize = 20;

    public static ChatActivity activityInstance;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.em_activity_chat);
        ButterKnife.bind(this);
        activityInstance = this;

        toChatUsername = getIntent().getStringExtra(EaseConstant.EXTRA_USER_ID);
        chatType =
                getIntent().getIntExtra(EaseConstant.EXTRA_CHAT_TYPE, EaseConstant.CHATTYPE_SINGLE);

        setToolbarTitle();
        getActionBarToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                finish();
            }
        });

        // init message list view
        mMessageListView.init(toChatUsername, chatType, newCustomChatRowProvider());
        registerForContextMenu(mMessageListView);
        mMessageListView.setItemClickListener(
                new EaseMessageListView.MessageListItemClicksListener() {
                    @Override public void onResendClick(EMMessage message) {
                        resendMessage(message);
                    }

                    @Override public boolean onBubbleClick(EMMessage message) {
                        // override you want click listener and return true
                        return false;
                    }

                    @Override public void onBubbleLongClick(EMMessage message) {

                    }

                    @Override public void onUserAvatarClick(String username) {

                    }

                    @Override public void onUserAvatarLongClick(String username) {

                    }
                });

        mInputView.setViewEventListener(new ChatInputView.ChatInputViewEventListener() {
            @Override public void onSendMessage(CharSequence content) {
                if (!TextUtils.isEmpty(content)) {
                    // create a message
                    EMMessage message =
                            EMMessage.createTxtSendMessage(content.toString(), toChatUsername);
                    // send message
                    EMClient.getInstance().chatManager().sendMessage(message);
                    // refresh ui
                    mMessageListView.refreshSelectLast();
                }
            }

            @Override public void onMicClick() {
                final VoiceRecordDialog dialog = new VoiceRecordDialog(ChatActivity.this);
                dialog.setRecordCallback(new VoiceRecordView.VoiceRecordCallback() {
                    @Override
                    public void onVoiceRecordComplete(String voiceFilePath, int voiceTimeLength) {
                        dialog.dismiss();
                        sendVoiceMessage(voiceFilePath, voiceTimeLength);
                    }
                });
                dialog.show();

            }
        });
        // received messages code in onResume() method

        //get the conversation
        conversation = EMClient.getInstance().chatManager()
                .getConversation(toChatUsername, EaseCommonUtils.getConversationType(chatType),
                        true);
        conversation.markAllMessagesAsRead();
        // the number of messages loaded into conversation is getChatOptions().getNumberOfMessagesLoaded
        // you can change this number
        final List<EMMessage> msgs = conversation.getAllMessages();
        int msgCount = msgs != null ? msgs.size() : 0;
        if (msgCount < conversation.getAllMsgCount() && msgCount < pageSize) {
            String msgId = null;
            if (msgs != null && msgs.size() > 0) {
                msgId = msgs.get(0).getMsgId();
            }
            conversation.loadMoreMsgFromDB(msgId, pageSize - msgCount);
        }
    }

    private void setToolbarTitle() {
        String nick = toChatUsername;
        if(chatType == EaseConstant.CHATTYPE_SINGLE){ //p2p chat
            UserEntity user = DemoHelper.getInstance().getContactList().get(toChatUsername);
            if(user != null){
                nick = user.getNickname();
            }
        }else if(chatType == EaseConstant.CHATTYPE_GROUP){ //group chat
            EMGroup group = EMClient.getInstance().groupManager().getGroup(toChatUsername);
            if(group != null){
                nick = group.getGroupName();
            }
        }
        getSupportActionBar().setTitle(nick);
    }


    @Override public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        //add the action buttons to toolbar
        Toolbar toolbar = getActionBarToolbar();
        toolbar.inflateMenu(R.menu.em_chat_menu);

        if (chatType == EaseConstant.CHATTYPE_GROUP) {

            menu.findItem(R.id.menu_group_detail).setVisible(true);
            menu.findItem(R.id.menu_voice_call).setVisible(false);
            menu.findItem(R.id.menu_video_call).setVisible(false);
        }

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_take_photo:
                        selectPicFromCamera();
                        break;
                    case R.id.menu_gallery:
                        selectPicFromLocal();
                        break;
                    case R.id.menu_location:
                        selectLoaction();
                        break;
                    case R.id.menu_file:
                        selectFileFromLocal();
                        break;
                    case R.id.menu_video_call:
                        Intent videoIntent = new Intent();
                        videoIntent.setClass(ChatActivity.this, VideoCallActivity.class);
                        videoIntent.putExtra(EaseConstant.EXTRA_USER_ID, toChatUsername);
                        videoIntent.putExtra(EaseConstant.EXTRA_IS_INCOMING_CALL, false);
                        startActivity(videoIntent);
                        break;
                    case R.id.menu_voice_call:
                        Intent voiceIntent = new Intent();
                        voiceIntent.setClass(ChatActivity.this, VoiceCallActivity.class);
                        voiceIntent.putExtra(EaseConstant.EXTRA_USER_ID, toChatUsername);
                        voiceIntent.putExtra(EaseConstant.EXTRA_IS_INCOMING_CALL, false);
                        startActivity(voiceIntent);
                        break;

                    case R.id.menu_group_detail:
                        startActivity(new Intent(ChatActivity.this, GroupDetailsActivity.class).putExtra("groupId",toChatUsername));
                        break;
                }

                return false;
            }
        });

        return true;
    }

    //methods of send various types message
    protected void sendVoiceMessage(String filePath, int length) {
        EMMessage message = EMMessage.createVoiceSendMessage(filePath, length, toChatUsername);
        sendMessage(message);
    }

    protected void sendImageMessage(String imagePath) {
        EMMessage message = EMMessage.createImageSendMessage(imagePath, false, toChatUsername);
        sendMessage(message);
    }

    protected void sendLocationMessage(double latitude, double longitude, String locationAddress) {
        EMMessage message =
                EMMessage.createLocationSendMessage(latitude, longitude, locationAddress,
                        toChatUsername);
        sendMessage(message);
    }

    protected void sendVideoMessage(String videoPath, String thumbPath, int videoLength) {
        EMMessage message =
                EMMessage.createVideoSendMessage(videoPath, thumbPath, videoLength, toChatUsername);
        sendMessage(message);
    }

    protected void sendFileMessage(String filePath) {
        EMMessage message = EMMessage.createFileSendMessage(filePath, toChatUsername);
        sendMessage(message);
    }

    protected void sendMessage(EMMessage message) {
        if (message == null) {
            return;
        }
        onSetMessageAttributes(message);
        if (chatType == EaseConstant.CHATTYPE_GROUP) {
            message.setChatType(EMMessage.ChatType.GroupChat);
        } else if (chatType == EaseConstant.CHATTYPE_CHATROOM) {
            message.setChatType(EMMessage.ChatType.ChatRoom);
        }
        //send message
        EMClient.getInstance().chatManager().sendMessage(message);
        //refresh ui
        mMessageListView.refreshSelectLast();
    }

    public void resendMessage(EMMessage message) {
        message.setStatus(EMMessage.Status.CREATE);
        EMClient.getInstance().chatManager().sendMessage(message);
        mMessageListView.refresh();
    }

    /**
     * send image
     */
    protected void sendPicByUri(Uri selectedImage) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        Cursor cursor =
                this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            cursor = null;

            if (picturePath == null || picturePath.equals("null")) {
                Toast toast = Toast.makeText(this, R.string.cant_find_pictures, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return;
            }
            sendImageMessage(picturePath);
        } else {
            File file = new File(selectedImage.getPath());
            if (!file.exists()) {
                Toast toast = Toast.makeText(this, R.string.cant_find_pictures, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return;
            }
            sendImageMessage(file.getAbsolutePath());
        }
    }

    /**
     * send file
     */
    protected void sendFileByUri(Uri uri) {
        String filePath = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
            Cursor cursor = null;

            try {
                cursor = this.getContentResolver().query(uri, filePathColumn, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            filePath = uri.getPath();
        }
        File file = new File(filePath);
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.File_does_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        //limit the size < 10M
        if (file.length() > 10 * 1024 * 1024) {
            Toast.makeText(this, R.string.The_file_is_not_greater_than_10_m, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        sendFileMessage(filePath);
    }

    /**
     * capture new image
     */
    protected void selectPicFromCamera() {
        if (!EaseCommonUtils.isSdcardExist()) {
            Toast.makeText(this, R.string.sd_card_does_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        cameraFile = new File(PathUtil.getInstance().getImagePath(),
                EMClient.getInstance().getCurrentUser() + System.currentTimeMillis() + ".jpg");
        cameraFile.getParentFile().mkdirs();
        startActivityForResult(
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(cameraFile)), REQUEST_CODE_CAMERA);
    }

    /**
     * select local image
     */
    protected void selectPicFromLocal() {
        Intent intent;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_LOCAL);
    }

    /**
     * select a file
     */
    protected void selectFileFromLocal() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        } else {
            //19 after this api is not available, demo here simply handle into the gallery to select the picture
            intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
    }

    protected void selectLoaction() {
        try {
            PlacePicker.IntentBuilder intentBuilder = new PlacePicker.IntentBuilder();
            Intent intent = intentBuilder.build(this);
            // Start the Intent by requesting a result, identified by a request code.
            startActivityForResult(intent, REQUEST_CODE_MAP);
        } catch (GooglePlayServicesRepairableException e) {
            GooglePlayServicesUtil.getErrorDialog(e.getConnectionStatusCode(), this, 0);
        } catch (GooglePlayServicesNotAvailableException e) {
            Toast.makeText(this, "Google Play Services is not available.", Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_CAMERA) { // capture new image
                if (cameraFile != null && cameraFile.exists()) {
                    sendImageMessage(cameraFile.getAbsolutePath());
                }
            } else if (requestCode == REQUEST_CODE_LOCAL) { // send local image
                if (data != null) {
                    Uri selectedImage = data.getData();
                    if (selectedImage != null) {
                        sendPicByUri(selectedImage);
                    }
                }
            } else if (requestCode == REQUEST_CODE_MAP) { // location
                final Place place = PlacePicker.getPlace(data, this);
                double latitude = place.getLatLng().latitude;
                double longitude = place.getLatLng().longitude;
                String locationAddress = (String) place.getAddress();

                if (locationAddress != null && !locationAddress.equals("")) {
                    sendLocationMessage(latitude, longitude, locationAddress);
                } else {
                    Toast.makeText(this, R.string.unable_to_get_location, Toast.LENGTH_SHORT)
                            .show();
                }
            } else if (requestCode == REQUEST_CODE_SELECT_FILE) { //send the file
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        sendFileByUri(uri);
                    }
                }
            }
        }
    }

    /**
     * create a chat row provider
     * @return
     */
    private EaseCustomChatRowProvider newCustomChatRowProvider() {
        return new EaseCustomChatRowProvider() {
            @Override public int getCustomChatRowTypeCount() {
                return 2;
            }

            @Override public int getCustomChatRowType(EMMessage message) {
                if (message.getBooleanAttribute(EaseConstant.MESSAGE_ATTR_IS_VIDEO_CALL, false)
                        || message.getBooleanAttribute(EaseConstant.MESSAGE_ATTR_IS_VOICE_CALL,
                        false)) {
                    return message.direct() == EMMessage.Direct.RECEIVE ? MESSAGE_TYPE_RECV_CALL
                            : MESSAGE_TYPE_SENT_CALL;
                }
                return 0;
            }

            @Override public EaseChatRow getCustomChatRow(EMMessage message, int position,
                    BaseAdapter adapter) {
                if (message.getBooleanAttribute(EaseConstant.MESSAGE_ATTR_IS_VIDEO_CALL, false)
                        || message.getBooleanAttribute(EaseConstant.MESSAGE_ATTR_IS_VOICE_CALL,
                        false)) {
                    return new ChatRowCall(ChatActivity.this, message, position, adapter);
                }
                return null;
            }
        };
    }

    /**
     * set message Extension attributes
     */
    protected void onSetMessageAttributes(EMMessage message) {

    }

    EMMessageListener mMessageListener = new EMMessageListener() {
        @Override public void onMessageReceived(List<EMMessage> list) {
            for (EMMessage message : list) {
                String username = null;
                // group message
                if (message.getChatType() == EMMessage.ChatType.GroupChat
                        || message.getChatType() == EMMessage.ChatType.ChatRoom) {
                    username = message.getTo();
                } else {
                    // single chat message
                    username = message.getFrom();
                }

                // if the message is for current conversation
                if (username.equals(toChatUsername)) {
                    mMessageListView.refreshSelectLast();
                    DemoHelper.getInstance().getNotifier().vibrateAndPlayTone(message);
                } else {
                    DemoHelper.getInstance().getNotifier().onNewMsg(message);
                }
            }
        }

        @Override public void onCmdMessageReceived(List<EMMessage> list) {
            //cmd messages do not save to the cache in sdk
        }

        @Override public void onMessageRead(List<EMMessage> list) {
            mMessageListView.refresh();
        }

        @Override public void onMessageDelivered(List<EMMessage> list) {
            mMessageListView.refresh();
        }

        @Override public void onMessageChanged(EMMessage emMessage, Object o) {
            mMessageListView.refresh();
        }
    };

    @Override protected void onResume() {
        super.onResume();
        DemoHelper.getInstance().pushActivity(this);
        // register the event listener when enter the foreground
        // remember to remove this listener in onStop()
        EMClient.getInstance().chatManager().addMessageListener(mMessageListener);

        mMessageListView.refresh();
    }

    @Override protected void onStop() {
        super.onStop();
        // unregister this event listener when this activity enters the background
        EMClient.getInstance().chatManager().removeMessageListener(mMessageListener);
        // remove activity from foreground activity list
        DemoHelper.getInstance().popActivity(this);
    }
}
