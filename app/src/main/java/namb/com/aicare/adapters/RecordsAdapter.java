package namb.com.aicare.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.util.List;

import namb.com.aicare.GlideApp;
import namb.com.aicare.R;

public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.ImageViewHolder> {
    private Context mContext;
    private List<DataSnapshot> mUploads;
    private String mUid;

    public RecordsAdapter(Context context, List<DataSnapshot> uploads, String currUserUid) {
        mContext = context;
        mUploads = uploads;
        mUid = currUserUid;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.adapter_records, parent, false);
        return new ImageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ImageViewHolder holder, int position) {
        DataSnapshot snapshot = mUploads.get(getItemCount()-1-position);
        holder.nameTextView.setText(snapshot.getKey());
        holder.resultTextView.setText(String.valueOf(snapshot.getValue()));
        GlideApp.with(mContext)
                .load(FirebaseStorage.getInstance().getReference(mUid).child(snapshot.getKey().replaceAll("[^a-zA-Z0-9]", "") + ".jpg"))
                .placeholder(R.mipmap.ic_launcher)
                .centerCrop()
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mUploads.size();
    }

    public class ImageViewHolder extends RecyclerView.ViewHolder {
        public TextView nameTextView;
        public TextView resultTextView;
        public ImageView imageView;

        public ImageViewHolder(View itemView) {
            super(itemView);

            nameTextView = itemView.findViewById(R.id.name_text_view);
            resultTextView = itemView.findViewById(R.id.result_text_view);
            imageView = itemView.findViewById(R.id.upload_image_view);
        }
    }
}