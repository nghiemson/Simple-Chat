/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.simplechat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        ImageView messageImageView;
        TextView messengerTextView;
        CircleImageView messengerImageView;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messageImageView = (ImageView) itemView.findViewById(R.id.messageImageView);
            messengerTextView = (TextView) itemView.findViewById(R.id.messengerTextView);
            messengerImageView = (CircleImageView) itemView.findViewById(R.id.messengerImageView);
        }
    }

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_INVITE = 1;
    private static final int REQUEST_IMAGE = 2;
    private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 10;
    public static final String ANONYMOUS = "anonymous";
    private static final String MESSAGE_SENT_EVENT = "message_sent";
    private String username;
    private String photoUrl;
    private SharedPreferences sharedPreferences;
    private GoogleSignInClient signInClient;
    private static final String MESSAGE_URL = "http://friendlychat.firebase.google.com/message/";

    private Button sendButton;
    private RecyclerView messageRecyclerView;
    private LinearLayoutManager linearLayoutManager;
    private ProgressBar progressBar;
    private EditText messageEditText;
    private ImageView addMessageImageView;

    // Firebase instance variables
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private DatabaseReference databaseReference;
    private FirebaseRecyclerAdapter<SimpleMessage, MessageViewHolder> firebaseAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Set default username is anonymous.
        username = ANONYMOUS;
        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        if(firebaseUser == null){
            //Not signed in, launch the Sign In Activity
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        } else {
            username = firebaseUser.getDisplayName();
            if(firebaseUser.getPhotoUrl() != null){
                photoUrl = firebaseUser.getPhotoUrl().toString();
            }
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);

        // Initialize ProgressBar and RecyclerView.
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        messageRecyclerView.setLayoutManager(linearLayoutManager);

        //New messages
        databaseReference = FirebaseDatabase.getInstance().getReference();
        SnapshotParser<SimpleMessage> parser = new SnapshotParser<SimpleMessage>() {
            @NonNull
            @Override
            public SimpleMessage parseSnapshot(@NonNull DataSnapshot snapshot) {
                SimpleMessage simpleMessage = snapshot.getValue(SimpleMessage.class);
                if(simpleMessage != null){
                    simpleMessage.setId(snapshot.getKey());
                }
                return simpleMessage;
            }
        };

        DatabaseReference mesRef = databaseReference.child(MESSAGES_CHILD);
        FirebaseRecyclerOptions<SimpleMessage> options = new FirebaseRecyclerOptions
                .Builder<SimpleMessage>().setQuery(mesRef, parser).build();
        firebaseAdapter = new FirebaseRecyclerAdapter<SimpleMessage, MessageViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull final MessageViewHolder holder, int position, @NonNull SimpleMessage model) {
                progressBar.setVisibility(ProgressBar.INVISIBLE);
                if(model.getText() != null){
                    holder.messageTextView.setText(model.getText());
                    holder.messageTextView.setVisibility(TextView.VISIBLE);
                    holder.messageImageView.setVisibility(ImageView.GONE);
                } else if (model.getImageUrl() != null) {
                    String imgUrl = model.getImageUrl();
                    if(imgUrl.startsWith("gs://")) {
                        StorageReference storageReference = FirebaseStorage.getInstance()
                                .getReferenceFromUrl(imgUrl);
                        storageReference.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                            @Override
                            public void onComplete(@NonNull Task<Uri> task) {
                                if(task.isSuccessful()) {
                                    String downloadUrl = task.getResult().toString();
                                    Glide.with(holder.messageImageView.getContext())
                                            .load(downloadUrl).into(holder.messageImageView);
                                } else {
                                    Log.w(TAG, "Download wasn't successful.", task.getException());
                                }
                            }
                        });
                    } else {
                        Glide.with(holder.messageImageView.getContext())
                                .load(model.getImageUrl()).into(holder.messageImageView);
                    }
                    holder.messageImageView.setVisibility(ImageView.VISIBLE);
                    holder.messageTextView.setVisibility(TextView.GONE);
                }

                holder.messageTextView.setText(model.getName());
                if(model.getPhotoUrl() == null) {
                    holder.messageImageView.setImageDrawable(ContextCompat.getDrawable(MainActivity.this,
                            R.drawable.ic_account_circle_black_36dp));
                } else {
                    Glide.with(MainActivity.this).load(model.getPhotoUrl())
                            .into(holder.messageImageView);
                }
                firebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onItemRangeInserted(int positionStart, int itemCount) {
                        super.onItemRangeInserted(positionStart, itemCount);
                        int messageCount = firebaseAdapter.getItemCount();
                        int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();

                        if(lastVisiblePosition == -1 || (positionStart >= (messageCount -1)
                        && lastVisiblePosition == (positionStart - 1))) {
                            messageRecyclerView.scrollToPosition(positionStart);
                        }

                }
                });
            }


            @NonNull
            @Override
            public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new MessageViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false));
            }
        };

        messageEditText = (EditText) findViewById(R.id.messageEditText);
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    sendButton.setEnabled(true);
                } else {
                    sendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Send messages on click.
            }
        });

        addMessageImageView = (ImageView) findViewById(R.id.addMessageImageView);
        addMessageImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Select image for image message on click.
            }
        });


    }


    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in.
        // TODO: Add code to check if user is signed in.
    }

    @Override
    public void onPause() {
        firebaseAdapter.stopListening();
        super.onPause();
    }

    @Override
    public void onResume() {
        firebaseAdapter.startListening();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.sign_out_menu:
                firebaseAuth.signOut();
                signInClient.signOut();
                username = ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }
}
