package edu.rutgers.axs.ucb.math;

import java.io.*;

/** Represents the user's parameters in the UCB recommender
 * @author Bangrui 
 *
 */
public class UCBProfile {
    double[][]  Sigma;
    double[] mu;
    // This lambda is the noise, which is predefined.
    // For test purpose, we can first set it to be 0.01.
    static double lambda = 0.1;
    UCBProfile(double [][] _Sigma, double[] _mu)  
    { Sigma =_Sigma; mu = _mu; }
    /** Updates this user profile, replacing the values (Sigma_t, mu_t)
	with (Sigma_{t+1}, mu_{t+1} ).

	@param X feature vectors for the articles on which the 
	user has provided feedback since the last profile update.
	@param Y the user feedback values for the documents in X
    */
    public void  updateProfile(double [][] X, double [] Y) {
	assert (Y.length == X.length);
	int num_updates = Y.length;
	for (int i = 0; i < num_updates; i++) {
	    this.update(X[i], Y[i]);
	}
   }


    /**
       Updates this user profile based on the user's feedback on a a single document.

       <pre>
     Reference: Discrete Square Root Filtering: A Survey of Current Techniques
     Paul G. Kaminski, Arthur E. Bryson, JR, Stanley F. Schmidt
     II. Conventional Filtering
     </pre>
      Here Phi(k)=I, B(k)=0, C(k) is our feature vector, Theta(k)=lambda^{2}

      @param feature the document vector
      @param reward the feedback value
    */
    public void update(double[] feature, double reward) {
	int feature_num = feature.length;
	double temp = 0;
	double[] zero_vector = new double[feature_num];
	double[] temp_vector = new double[feature_num];
	
	// temp = C(k)Sigma(k)C^{T}(k)
	for (int i = 0; i < feature_num; i++) {
	    for (int j = 0; j < feature_num; j++) {
		temp_vector[i] = temp_vector[i] + feature[j] * this.Sigma[i][j];
	    }
	    temp = temp + temp_vector[i] * feature[i];
	}
		
	// temp = [Theta(k) + C(k)Sigma(k)C^{T}(k)]^{-1}
	temp = 1 / (temp + lambda * lambda);
	temp_vector = zero_vector;
	
	// (7) temp_vector = K(k)
	for (int i = 0; i < feature_num; i++) {
	    for (int j = 0; j < feature_num; j++) {
		temp_vector[i] = temp_vector[i] + this.Sigma[i][j] * feature[j];
	    }
	    temp_vector[i] = temp_vector[i] * temp;
	}
	    
	// (6) temp_vector2 = I - K(k)C(k)
	double[][] temp_vector2 = new double[feature_num][feature_num];
	for (int i = 0; i < feature_num; i++) {
	    for (int j = 0; j < feature_num; j++) {
		if (i == j) {
		    temp_vector2[i][j] = 1 - temp_vector[i] * feature[j];
		} else {
		    temp_vector2[i][j] = - temp_vector[i] * feature[j];
		}
	    }
	}
	    
	double[][] norm_cov_temp = new double[feature_num][feature_num];

	// Sigma_plus(k) = [I - K(k)C(k)]Sigma(k)
	for (int i = 0; i < feature_num; i++) {
	    for (int j = 0; j < feature_num; j++) {
		for (int k = 0; k < feature_num; k++) {
		    norm_cov_temp[i][j]= norm_cov_temp[i][j] +
			temp_vector2[i][k] * this.Sigma[k][j];
		}
	    }
	}
	this.Sigma = norm_cov_temp;
	    
	// z(k) - C(k)hat{x}(k)
	double reward_corr = reward;
	for (int i = 0; i < feature_num; i++)
	    reward_corr = reward_corr - feature[i] * this.mu[i];
	// hat{x}(x) + K(k)[z(k) - C(k)hat{x}(k)]
	for (int i = 0; i < feature_num; i++)
	    this.mu[i] = this.mu[i] + temp_vector[i] * reward_corr;
    }

    static public UCBProfile readProfile(File f, int L) throws IOException {
	LineNumberReader r = new LineNumberReader(new FileReader(f));

	double[][]  Sigma = new double[L][];
	double[] mu=null;
  

	String s=null;
	int lineNo = 0;
	while((s = r.readLine())!=null) {
	    if (lineNo > L)  throw new IllegalArgumentException("UCBProfile.readProfile(" + f + "): found more than expected " + L + "+1 lines");
	    String[] q=s.split("\\s+");
	    if (q.length != L) throw new IllegalArgumentException("UCBProfile.readProfile(" + f + "): found " + q.length + " tokens on line " + r.getLineNumber() + ", while expecting " + L);
	    double [] v = new double[L];
	    for(int i=0; i<L; i++) {
		v[i] = Double.valueOf(q[i]);
	    }
	    if (lineNo==0) mu = v;
	    else Sigma[lineNo-1]=v;	    
	    lineNo++;
	}
	if (lineNo!=L+1) throw new IllegalArgumentException("UCBProfile.readProfile(" + f + "): found onlu "+lineNo+" lines, fewer than expected " + L + "+1 lines");
	return new UCBProfile(Sigma,mu);
    }
}
