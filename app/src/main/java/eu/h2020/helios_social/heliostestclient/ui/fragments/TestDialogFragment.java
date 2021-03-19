package eu.h2020.helios_social.heliostestclient.ui.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.EditText;

import eu.h2020.helios_social.heliostestclient.R;

/**
 * Not used Test dialog. Should be removed.
 */
public class TestDialogFragment extends DialogFragment {
    private OnFragmentInteractionListener mListener;
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(inflater.inflate(R.layout.dialog_new, null))
                // Add action buttons
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Get the values from the Dialog views and send
                        String text = ((EditText)((AlertDialog) dialog).findViewById(R.id.username)).getText().toString();
                        onButtonPressed(text);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        TestDialogFragment.this.getDialog().cancel();
                    }
                });

        // title
        builder.setTitle("Choose context (available listed)");

        // add a checkbox list
        String[] animals = {"Location (x,y)", "Dog Park X", "Game of Thrones", "Dogs", "HELIOS"};
        boolean[] checkedItems = {true, false, false, true, false};
        builder.setMultiChoiceItems(animals, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                // user checked or unchecked a box
            }
        });
        return builder.create();
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void onButtonPressed(String json) {
        if (mListener != null) {
            mListener.onFragmentInteraction(json);
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(String json);
    }
}
