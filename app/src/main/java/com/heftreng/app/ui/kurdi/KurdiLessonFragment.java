package com.heftreng.app.ui.kurdi;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.heftreng.app.R;
import com.heftreng.app.model.*;
import java.util.*;

public class KurdiLessonFragment extends Fragment {

    private String unitId, unitTitle;
    private int unitXP;
    private List<KurdiVocab> vocabList = new ArrayList<>();
    private int currentIndex = 0;
    private int score = 0;

    private TextView tvProgress, tvQuestion, tvUnitTitle;
    private LinearLayout llChoices;
    private View btnNext;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    public static KurdiLessonFragment newInstance(Bundle args) {
        KurdiLessonFragment f = new KurdiLessonFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_kurdi_lesson, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        super.onViewCreated(view, s);
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        Bundle args = getArguments();
        if (args != null) {
            unitId    = args.getString("unitId");
            unitTitle = args.getString("unitTitle");
            unitXP    = args.getInt("unitXP", 10);
        }

        tvProgress  = view.findViewById(R.id.tvProgress);
        tvQuestion  = view.findViewById(R.id.tvQuestion);
        tvUnitTitle = view.findViewById(R.id.tvUnitTitle);
        llChoices   = view.findViewById(R.id.llChoices);
        btnNext     = view.findViewById(R.id.btnNext);

        if (tvUnitTitle != null) tvUnitTitle.setText(unitTitle);
        if (btnNext     != null) btnNext.setVisibility(View.GONE);

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());

        loadVocab();
    }

    private void loadVocab() {
        db.collection("kurdiVocab")
            .whereEqualTo("unitId", unitId)
            .limit(10)
            .get()
            .addOnSuccessListener(snap -> {
                if (!isAdded()) return;
                vocabList.clear();
                for (QueryDocumentSnapshot doc : snap) {
                    KurdiVocab v = doc.toObject(KurdiVocab.class);
                    v.id = doc.getId();
                    vocabList.add(v);
                }
                Collections.shuffle(vocabList);
                if (!vocabList.isEmpty()) showQuestion();
                else showComplete();
            });
    }

    private void showQuestion() {
        if (currentIndex >= vocabList.size()) { showComplete(); return; }
        KurdiVocab vocab = vocabList.get(currentIndex);

        if (tvProgress != null)
            tvProgress.setText((currentIndex + 1) + "/" + vocabList.size());
        if (tvQuestion != null)
            tvQuestion.setText(vocab.ku);

        llChoices.removeAllViews();
        if (btnNext != null) btnNext.setVisibility(View.GONE);

        // 4 şık oluştur: 1 doğru + 3 yanlış
        List<String> choices = new ArrayList<>();
        choices.add(vocab.tr);
        List<KurdiVocab> others = new ArrayList<>(vocabList);
        others.remove(vocab);
        Collections.shuffle(others);
        for (int i = 0; i < Math.min(3, others.size()); i++)
            choices.add(others.get(i).tr);
        Collections.shuffle(choices);

        for (String choice : choices) {
            Button btn = new Button(requireContext());
            btn.setText(choice);
            btn.setBackgroundColor(getResources().getColor(R.color.surface2, null));
            btn.setTextColor(getResources().getColor(R.color.white, null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 8);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                boolean correct = choice.equals(vocab.tr);
                if (correct) { score++; btn.setBackgroundColor(0xFF2ECC71); }
                else         { btn.setBackgroundColor(0xFFE74C3C); }
                // Doğru cevabı göster
                for (int i = 0; i < llChoices.getChildCount(); i++) {
                    View child = llChoices.getChildAt(i);
                    if (child instanceof Button) {
                        child.setEnabled(false);
                        if (((Button)child).getText().equals(vocab.tr))
                            child.setBackgroundColor(0xFF2ECC71);
                    }
                }
                if (btnNext != null) {
                    btnNext.setVisibility(View.VISIBLE);
                    btnNext.setOnClickListener(nv -> {
                        currentIndex++;
                        showQuestion();
                    });
                }
            });
            llChoices.addView(btn);
        }
    }

    private void showComplete() {
        if (tvQuestion != null)
            tvQuestion.setText("Ders tamamlandı! 🎉\n\nSkor: " + score + "/" + vocabList.size());
        if (llChoices != null) llChoices.removeAllViews();
        if (btnNext != null) {
            btnNext.setVisibility(View.VISIBLE);
            ((TextView) btnNext.findViewById(android.R.id.text1) != null ?
                (TextView) btnNext : (View) btnNext).setVisibility(View.VISIBLE);
            btnNext.setOnClickListener(v -> {
                saveProgress();
                requireActivity().getSupportFragmentManager().popBackStack();
            });
        }
        Button btnFinish = new Button(requireContext());
        btnFinish.setText("Bitir ve XP Kazan (+"+unitXP+" XP)");
        btnFinish.setBackgroundColor(getResources().getColor(R.color.brand_primary, null));
        btnFinish.setTextColor(0xFFFFFFFF);
        if (llChoices != null) llChoices.addView(btnFinish);
        btnFinish.setOnClickListener(v -> {
            saveProgress();
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }

    private void saveProgress() {
        if (currentUser == null) return;
        DocumentReference ref = db.collection("kurdiProgress")
            .document(currentUser.getUid());
        ref.get().addOnSuccessListener(doc -> {
            long curXP = 0;
            if (doc.exists() && doc.getLong("xp") != null)
                curXP = doc.getLong("xp");
            Map<String, Object> data = new HashMap<>();
            data.put("xp", curXP + unitXP);
            data.put("completedUnits." + unitId, true);
            ref.set(data, SetOptions.merge());
            Toast.makeText(getContext(), "+" + unitXP + " XP kazandın! 🎉",
                Toast.LENGTH_SHORT).show();
        });
    }
}
