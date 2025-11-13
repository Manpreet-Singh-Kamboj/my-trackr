package com.mytrackr.receipts.features.edit_profile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mytrackr.receipts.R;
import com.mytrackr.receipts.databinding.ActivityEditProfileBinding;
import com.mytrackr.receipts.viewmodels.AuthViewModel;

public class EditProfileActivity extends AppCompatActivity {

    private static final int GALLERY_PERMISSION_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;
    private ActivityEditProfileBinding binding;
    private AuthViewModel authViewModel;
    private Uri newProfilePictureUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        binding.editProfileToolbar.toolbarTitle.setText(getString(R.string.edit_profile));
        binding.editProfileToolbar.toolbar.setNavigationOnClickListener(v->{
            getOnBackPressedDispatcher().onBackPressed();
        });
        authViewModel.getUserDetails().observe(this,user->{
            if(user != null){
                Glide.with(binding.getRoot().getContext())
                        .load(user.getProfilePicture())
                        .circleCrop()
                        .placeholder(R.drawable.ic_user_avatar)
                        .error(R.drawable.ic_user_avatar)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .into(binding.profileImageView);
                binding.userFullName.setText(user.getFullName());
                binding.email.setText(user.getEmail());
                if(user.getAboutMe() != null){
                    binding.aboutMe.setText(user.getAboutMe());
                }
                if(user.getPhoneNo() != null){
                    binding.phoneNumber.setText(user.getPhoneNo());
                }
                if(user.getCity() != null){
                    binding.city.setText(user.getCity());
                }
            }
        });
        binding.editProfileImageButton.setOnClickListener(this::handleImageSelect);
        binding.saveChanges.setOnClickListener(this::handleSaveChanges);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            binding.profileImageView.setImageURI(selectedImageUri);
            newProfilePictureUri = selectedImageUri;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GALLERY_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Permission denied for accessing gallery.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }
    private void checkAndRequestGalleryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, GALLERY_PERMISSION_REQUEST_CODE);
            } else {
                openGallery();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1234);
            } else {
                openGallery();
            }
        }
    }
    private void handleImageSelect(View view){
        checkAndRequestGalleryPermissions();
    }
    private void handleSaveChanges(View view) {
        authViewModel.updateUserProfile(binding,newProfilePictureUri);
    }

}