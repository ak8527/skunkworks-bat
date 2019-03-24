package org.odk.odknotifications.activities;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.odknotifications.databasecommunicator.DBHandler;
import org.odk.odknotifications.fragments.NotificationGroupFragment;
import org.odk.odknotifications.model.Group;
import org.odk.odknotifications.R;
import org.odk.odknotifications.SubscribeNotificationGroup;
import org.odk.odknotifications.UnsubscribeNotificationGroups;
import org.opendatakit.application.CommonApplication;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.database.service.UserDbInterface;
import org.opendatakit.exception.ServicesAvailabilityException;
import org.opendatakit.listener.DatabaseConnectionListener;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.utilities.ODKFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, DatabaseConnectionListener {

    //private String appName = "org.odk.odknotifications";
    public static String appName = ODKFileUtils.getOdkDefaultAppName();

    private DatabaseConnectionListener mIOdkDataDatabaseListener;
    private TextView name_tv;
    private String loggedInUsername;
    private final String TAG = "ODK Notifications";
    private PropertiesSingleton mPropSingleton;
    private DBHandler dbHandler;
    private ArrayList<Group> groupArrayList;
    public static final String ARG_GROUP_ID = "id";
    private final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE_CODE = 1;

    protected static final String[] STORAGE_PERMISSION = new String[]{
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        FirebaseApp.initializeApp(this);
        setSupportActionBar(toolbar);
        requestStoragePermission();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IntentIntegrator qrScan = new IntentIntegrator(MainActivity.this);
                qrScan.initiateScan();
            }
        });

        dbHandler = new DBHandler(this, null, null, 1);

        String appName = getIntent().getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
        if (appName == null) {
            appName = ODKFileUtils.getOdkDefaultAppName();
        }
        MainActivity.appName = appName;

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        final NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        View headerView = navigationView.getHeaderView(0);
        name_tv = headerView.findViewById(R.id.name_tv);

        addMenuItemInNavMenuDrawer();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public String loadJSONFromAsset() {
        String json;
        try {
            File jsonFile = new File(ODKFileUtils.getAssetsFolder(appName) + "/google-services.json");
            FileInputStream is = new FileInputStream(jsonFile);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
            Log.e("JSON", json);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            //if qrcode has nothing in it
            if (result.getContents() == null) {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            } else {
                //if qr contains data
                String link = result.getContents();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                startActivity(browserIntent);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void addMenuItemInNavMenuDrawer() {

        NavigationView navView = findViewById(R.id.nav_view);
        groupArrayList = dbHandler.getGroups();
        Menu menu = navView.getMenu();
        menu.clear();
        for (int i = 0; i < groupArrayList.size(); i++) {
            if (groupArrayList.get(i).getName() != null)
                menu.add(0, i, 0, groupArrayList.get(i).getName());
        }
        navView.invalidate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_sync) {
            syncCloudDatabase();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void syncCloudDatabase() {
        groupArrayList = getGroups();
        addGroupsFromFirebase();
        joinODKGroups(groupArrayList);
        addMenuItemInNavMenuDrawer();
    }

    private void addGroupsFromFirebase() {
        DatabaseReference mRef = FirebaseDatabase.getInstance().getReference().child("clients").child(getActiveUser());

        mRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot group : dataSnapshot.getChildren()) {
                    String name = (String) group.child("name").getValue();
                    String id = (String) group.child("id").getValue();
                    Group grp = new Group(id, name, 0);
                    groupArrayList.add(grp);
                    new SubscribeNotificationGroup(MainActivity.this, id, getActiveUser());
                    dbHandler.addNewGroup(grp);
                }
                addMenuItemInNavMenuDrawer();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        NotificationGroupFragment fragment = new NotificationGroupFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_GROUP_ID, groupArrayList.get(item.getItemId()).getId());
        FragmentManager manager = getSupportFragmentManager();
        fragment.setArguments(bundle);
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.commit();

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public String getActiveUser() {
        try {
            return getDatabase().getActiveUser(getAppName());
        } catch (ServicesAvailabilityException e) {
            WebLogger.getLogger(getAppName()).printStackTrace(e);
            return CommonToolProperties.ANONYMOUS_USER;
        }
    }


    public UserDbInterface getDatabase() {
        return ((CommonApplication) getApplication()).getDatabase();
    }

    public String getAppName() {
        return appName;
    }


    @Override
    public void databaseAvailable() {

        if (mIOdkDataDatabaseListener != null) {
            mIOdkDataDatabaseListener.databaseAvailable();
        }
        loggedInUsername = getActiveUser();
        getDeepLink();
        Log.e("Success", "Database available" + loggedInUsername);
        if (loggedInUsername != null) name_tv.setText(loggedInUsername);
    }

    @Override
    public void databaseUnavailable() {

        if (mIOdkDataDatabaseListener != null) {
            mIOdkDataDatabaseListener.databaseUnavailable();
        }
        Log.e("ERROR", "Database unavailable");

    }


    @Override
    protected void onResume() {
        super.onResume();
        ((CommonApplication) getApplication()).onActivityResume(this);
        ((CommonApplication) getApplication()).establishDoNotFireDatabaseConnectionListener(this);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        ((CommonApplication) getApplication()).fireDatabaseConnectionListener();
    }

    @Override
    protected void onPause() {
        ((CommonApplication) getApplication()).onActivityPause(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ((CommonApplication) getApplication()).onActivityDestroy(this);
        super.onDestroy();
    }

    public void getDeepLink() {
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, new OnSuccessListener<PendingDynamicLinkData>() {
                    @Override
                    public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {

                        // Get deep link from result (may be null if no link is found)
                        Uri deepLink = null;

                        if (pendingDynamicLinkData != null) {
                            deepLink = pendingDynamicLinkData.getLink();
                        }

                        try {
                            if (deepLink != null) {
                                URL url = new URL(deepLink.toString());
                                Map<String, String> map = splitQuery(url);
                                String id = map.get("id");
                                DatabaseReference mRef = FirebaseDatabase.getInstance().getReference();
                                if (id != null) {
                                    mRef.child("group").child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                            Group group = new Group((String) dataSnapshot.child("id").getValue(), (String) dataSnapshot.child("name").getValue(), 0);
                                            new SubscribeNotificationGroup(MainActivity.this, group.getId(), getActiveUser()).execute();
                                            DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child("clients").child(getActiveUser()).child("groups");
                                            databaseReference.child("id").setValue(group.getId());
                                            databaseReference.child("name").setValue(group.getName());
                                            dbHandler.addNewGroup(group);
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError databaseError) {

                                        }
                                    });
                                }

                            } else {
                                Log.d(TAG, "getDynamicLink: no link found");
                            }
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "getDynamicLink:onFailure", e);
                    }
                });
    }

    public static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }

    public ArrayList<Group> getGroups() {
        ArrayList<Group> groupsList = new ArrayList<>();
        try {
            String roles_array_string = getDatabase().getRolesList(getAppName());

            JSONArray rolesArray = new JSONArray(roles_array_string);
            for (int i = 0; i < rolesArray.length(); i++) {
                if (rolesArray.getString(i).startsWith("GROUP_") || rolesArray.getString(i).startsWith("ROLE_"))
                    groupsList.add(new Group(rolesArray.getString(i), rolesArray.getString(i), 0));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ServicesAvailabilityException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return groupsList;
    }

    public void joinODKGroups(ArrayList<Group> groupArrayList) {
        new UnsubscribeNotificationGroups(this).execute();
        for (Group group : groupArrayList) {
            new SubscribeNotificationGroup(this, group.getId(), getActiveUser()).execute();
        }
        dbHandler.newGroupDatabase(groupArrayList);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_READ_EXTERNAL_STORAGE_CODE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted.
                readConfigFile();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.storage_permission_rationale)
                        .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    //For pre Marshmallow devices, this wouldn't be called as they don't need runtime permission.
                                    requestPermissions(
                                            new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE_CODE);
                                }
                            }
                        })
                        .setNegativeButton(R.string.not_now, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                                MainActivity.this.finish();
                            }
                        });
                builder.create().show();
            }
        }
    }

    private void readConfigFile() {
        try {
            String json = loadJSONFromAsset();
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                //System.out.print(obj.toString());
                String databaseUrl = obj.getJSONObject("project_info").getString("firebase_url");
                String storageBucket = obj.getJSONObject("project_info").getString("storage_bucket");
                String applicationId = obj.getJSONArray("client").getJSONObject(0).getJSONObject("client_info").getString("mobilesdk_app_id");
                String apiKey = obj.getJSONArray("client").getJSONObject(0).getJSONArray("api_key").getJSONObject(0).getString("current_key");

                FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                        .setApplicationId(applicationId)
                        .setApiKey(apiKey)
                        .setDatabaseUrl(databaseUrl)
                        .setStorageBucket(storageBucket);
                FirebaseApp.initializeApp(this, builder.build());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(

                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                //For pre Marshmallow devices, this wouldn't be called as they don't need runtime permission.
                requestPermissions(
                        STORAGE_PERMISSION,
                        PERMISSION_REQUEST_READ_EXTERNAL_STORAGE_CODE
                );
            } else {
                // Permission has been granted. Read config file.
                readConfigFile();
            }
        }
    }
}


