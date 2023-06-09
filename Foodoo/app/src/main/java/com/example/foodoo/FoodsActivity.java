package com.example.foodoo;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class FoodsActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getName();

    // Firebase
    private FirebaseUser user;
    private FirebaseFirestore mFirestore;
    private CollectionReference mItems;

    // Notification handler
    private NotificationHandler mNotificationHandler;
    private JobScheduler mJobScheduler;

    private RecyclerView mRecyclerView;
    private ArrayList<FoodItem> mItemList;
    private FoodItemAdapter mFoodItemAdapter;


    private FrameLayout redCircle;
    private TextView countTextView;
    private int dailyIntakeItems = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foods);

        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Log.d(LOG_TAG, "Authenticated user");
        } else {
            Log.d(LOG_TAG, "Unauthenticated user!");
            finish();
        }

        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 1));
        mItemList = new ArrayList<>();

        mFoodItemAdapter = new FoodItemAdapter(this, mItemList);
        mRecyclerView.setAdapter(mFoodItemAdapter);

        mFirestore = FirebaseFirestore.getInstance();
        mItems = mFirestore.collection("Items");

        mNotificationHandler = new NotificationHandler(this);

        mJobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        setJobScheduler();
        queryData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // cleanup
        if (mJobScheduler != null) {
            mJobScheduler.cancelAll();
        }
    }

    private void queryData() {
        mItemList.clear();
        mItems.orderBy("name").get().addOnSuccessListener(queryDocumentSnapshots -> {
            for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                FoodItem item = documentSnapshot.toObject(FoodItem.class);
                item.setId(documentSnapshot.getId());
                mItemList.add(item);
            }
            if (mItemList.size() == 0) {
                initializeData();
                queryData();
            }
            int storedItems = 0;
            for (FoodItem item : mItemList) {
                if (item.getStoredCount() != 0) {
                    storedItems += item.getStoredCount();
                }
            }
            dailyIntakeItems = storedItems;

            if (dailyIntakeItems > 0) {
                countTextView.setText(String.valueOf(dailyIntakeItems));
            } else {
                countTextView.setText("");
            }
            redCircle.setVisibility((dailyIntakeItems > 0) ? VISIBLE : GONE);

            mFoodItemAdapter.notifyDataSetChanged();
        });
    }

    private void initializeData() {
        String[] foodItemNames = getResources().getStringArray(R.array.food_item_names);
        String[] foodItemCalories = getResources().getStringArray(R.array.food_item_calories);
        String[] foodItemPrices = getResources().getStringArray(R.array.food_item_price);
        TypedArray foodItemRates = getResources().obtainTypedArray(R.array.food_item_rates);

        for (int i = 0; i < foodItemNames.length; i++) {
            mItems.add(new FoodItem(foodItemNames[i], foodItemCalories[i], foodItemPrices[i], foodItemRates.getFloat(i, 0), 0));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.food_list_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.search_bar);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                Log.d(LOG_TAG, s);
                mFoodItemAdapter.getFilter().filter(s);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.logoutButton) {
            Log.d(LOG_TAG, "Log out clicked");
            FirebaseAuth.getInstance().signOut();
            finish();
            return true;
        } else if (itemId == R.id.setting_button) {
            Log.d(LOG_TAG, "Settings clicked");
            return true;
        } else if (itemId == R.id.daily_intake) {
            Log.d(LOG_TAG, "Daily intake clicked");
            item.getActionView().findViewById(R.id.daily_intake).setOnClickListener(view -> {
                goToStoredFoods(this);
            });
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void goToStoredFoods(Context context) {
        Intent intent = new Intent(context, IntakeActivity.class);
        context.startActivity(intent);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(LOG_TAG, "Menu: " + menu);
        final MenuItem alertMenuItem = menu.findItem(R.id.daily_intake);
        FrameLayout rootView = (FrameLayout) alertMenuItem.getActionView();

        redCircle = (FrameLayout) rootView.findViewById(R.id.view_alert_red_circle);
        countTextView = (TextView) rootView.findViewById(R.id.view_alert_count_textview);

        Log.d(LOG_TAG, "redCircle: " + redCircle);
        Log.d(LOG_TAG, "countTextView: " + countTextView);
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(alertMenuItem);
            }
        });
        return super.onPrepareOptionsMenu(menu);
    }

    public void updateAlertIcon(FoodItem item) {
        dailyIntakeItems++;
        if (dailyIntakeItems > 0) {
            countTextView.setText(String.valueOf(dailyIntakeItems));
        } else {
            countTextView.setText("");
        }
        redCircle.setVisibility((dailyIntakeItems > 0) ? VISIBLE : GONE);

        mItems.document(item._getId()).update("storedCount", item.getStoredCount() + 1).addOnFailureListener(failure -> {
            Toast.makeText(this, item.getName() + " nem változtatható meg.", Toast.LENGTH_LONG).show();
        });

        StringBuilder messageBuilder = new StringBuilder();
        for (FoodItem foodItem : mItemList) {
            if (foodItem.getStoredCount() > 0) {
                messageBuilder.append(foodItem.getName()).append(" x ").append(foodItem.getStoredCount()).append(", ");
            }
        }

        if (messageBuilder.length() > 0) {
            messageBuilder.setLength(messageBuilder.length() - 2);
        }

        mNotificationHandler.send("Jelenlegi ételeid", messageBuilder.toString());
        queryData();
    }

    public void deleteFood(FoodItem item) {
        DocumentReference reference = mItems.document(item._getId());

        reference.delete().addOnSuccessListener(success -> {
            Toast.makeText(this, item.getName() + " törölve lett.", Toast.LENGTH_LONG).show();

            dailyIntakeItems -= item.getStoredCount();
            if (dailyIntakeItems > 0) {
                countTextView.setText(String.valueOf(dailyIntakeItems));
            } else {
                countTextView.setText("");
            }
            redCircle.setVisibility((dailyIntakeItems > 0) ? VISIBLE : GONE);
        }).addOnFailureListener(failure -> {
            Toast.makeText(this, item.getName() + " nem törölhető!", Toast.LENGTH_LONG).show();
        });

        queryData();
        mNotificationHandler.cancel();
    }

    private void setJobScheduler() {
        int networkType = JobInfo.NETWORK_TYPE_UNMETERED;
        int hardDeadLine = 5000;

        ComponentName name = new ComponentName(getPackageName(), NotificationJobService.class.getName());
        JobInfo.Builder builder = new JobInfo.Builder(0, name)
                .setRequiredNetworkType(networkType)
                .setOverrideDeadline(hardDeadLine);

        mJobScheduler.schedule(builder.build());
    }
}