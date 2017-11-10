package com.mtesitoo;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.crashlytics.android.Crashlytics;
import com.mtesitoo.backend.cache.AuthorizationCache;
import com.mtesitoo.backend.cache.CountriesCache;
import com.mtesitoo.backend.cache.logic.IAuthorizationCache;
import com.mtesitoo.backend.cache.logic.ICountriesCache;
import com.mtesitoo.backend.model.Countries;
import com.mtesitoo.backend.service.CountriesRequest;
import com.mtesitoo.backend.service.ForgotPasswordRequest;
import com.mtesitoo.backend.service.LoginRequest;
import com.mtesitoo.backend.service.logic.ICallback;
import com.mtesitoo.backend.service.logic.ICountriesRequest;
import com.mtesitoo.backend.service.logic.IForgotPasswordRequest;
import com.mtesitoo.backend.service.logic.ILoginRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.fabric.sdk.android.Fabric;

public class LoginActivity extends AbstractLoginActivity implements View.OnClickListener {

    @BindView(R.id.user_name)
    TextView mUsername;

    @BindView(R.id.password)
    TextView mPassword;

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.login: {
                final Intent intent = new Intent(this, HomeActivity.class);
                logInUser(intent, mUsername.getText().toString().trim(), mPassword.getText().toString(), false);
                break;
            }

            case R.id.newUser: {
                //Todo: allow user to pick between seller and buyer profiles

                final Intent intent = new Intent(context, RegisterActivity.class);
                context.startActivity(intent);

                break;
            }

            case R.id.forgotPassword: {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Enter your registered email address");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //send request to forgot password API
                        String username = input.getText().toString();
                        if (!username.isEmpty()) {
                            forgotPassword(username);
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
                break;
            }
        }
    }

    private void forgotPassword(final String username) {
        final IForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest(context);
        forgotPasswordRequest.forgotPassword(username, new ICallback<String>() {
            @Override
            public void onResult(String result) {
                Toast.makeText(context, getString(R.string.forgot_password_request), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Exception e) {
                VolleyError err = (VolleyError) e;

                String errorMsg = e.getMessage();
                if (err.networkResponse != null &&
                        err.networkResponse.data != null) {
                    try {
                        String body = new String(err.networkResponse.data, "UTF-8");
                        Log.e("REG_ERR", body);
                        JSONObject jsonErrors = new JSONObject(body);
                        JSONObject error = jsonErrors.getJSONArray("errors").getJSONObject(0);
                        errorMsg = error.getString("message");
                    } catch (UnsupportedEncodingException encErr) {
                        encErr.printStackTrace();
                    } catch (JSONException jErr) {
                        jErr.printStackTrace();
                    } finally {
                        if (errorMsg.equals("")) {
                            errorMsg = getString(R.string.forgot_password_request_error);
                        }
                    }
                }

                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_login);

        ButterKnife.bind(this);

        Button login = (Button) findViewById(R.id.login);
        login.setOnClickListener(this);
        Button newUser = (Button) findViewById(R.id.newUser);
        newUser.setOnClickListener(this);
        Button forgotPassword = (Button) findViewById(R.id.forgotPassword);
        forgotPassword.setOnClickListener(this);

        //Handling password reset request
        Intent passwordResetIntent = getIntent();
        String action = passwordResetIntent.getAction();
        String data = passwordResetIntent.getDataString();

        //https://tesitoo.com/index.php?route=common/reset&code=84f6c6874e7ffd31fe3d84132c790824fc8b1024
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            if (data.contains("code")) {
                String code = data.substring(data.indexOf("&code=") + "&code=".length());
                logInByCode(new Intent(this, HomeActivity.class), code, true);
            } else {
                Toast.makeText(context, "Broken Password reset link.", Toast.LENGTH_LONG).show();
            }
        }
        //ends here

        ICountriesRequest countriesService = new CountriesRequest(context);

        countriesService.getCountries(new ICallback<List<Countries>>() {
            @Override
            public void onResult(List<Countries> countries) {
                ICountriesCache cache = new CountriesCache(context);
                cache.storeCountries(countries);
                String s1 = "";

                for (Countries country : countries) {
                    s1 = s1 + country.getName();
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e("Countries", e.toString());
            }
        });


        final ILoginRequest loginService = new LoginRequest(this);
        loginService.getAuthToken(new ICallback<String>() {
            @Override
            public void onResult(String result) {
                IAuthorizationCache authorizationCache = new AuthorizationCache(context);
                if (authorizationCache.getAuthorization() == null) {
                    authorizationCache.storeAuthorization(result);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e("Login", e.toString());
            }
        });

        //Automatically log-in for already logged-in user
        boolean isUserLoggedIn = sharedPreferences.getBoolean(Constants.IS_USER_LOGGED_IN_KEY, false);
        if (isUserLoggedIn) {
            showLoginProgress("Logging in");

            String username = sharedPreferences.getString(Constants.LOGGED_IN_USER_ID_KEY, "");
            String password = sharedPreferences.getString(Constants.LOGGED_IN_USER_PASS_KEY, "");
            logInUser(new Intent(this, HomeActivity.class), username, password, false);
        }
    }
}