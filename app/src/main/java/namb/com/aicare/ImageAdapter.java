package namb.com.aicare;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.List;

import namb.com.aicare.activities.GlideApp;


public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private Context mContext;
    private List<DataSnapshot> mUploads;
    private String mEmail;

    public ImageAdapter(Context context, List<DataSnapshot> uploads, String currUserEmail) {
        mContext = context;
        mUploads = uploads;
        mEmail = currUserEmail;
    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.image_item, parent, false);
        return new ImageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ImageViewHolder holder, int position) {
        DataSnapshot uploadCurrent = mUploads.get(getItemCount()-1-position);
        holder.textViewName.setText(String.valueOf(uploadCurrent.getValue()));
        GlideApp.with(mContext)
                .load(FirebaseStorage.getInstance().getReference().child(mEmail + "/" + uploadCurrent.getKey() + ".jpg"))
                .placeholder(R.mipmap.ic_launcher)
                .centerCrop()
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mUploads.size();
    }

    public class ImageViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewName;
        public ImageView imageView;

        public ImageViewHolder(View itemView) {
            super(itemView);

            textViewName = itemView.findViewById(R.id.text_view_name);
            imageView = itemView.findViewById(R.id.image_view_upload);
        }
    }
}