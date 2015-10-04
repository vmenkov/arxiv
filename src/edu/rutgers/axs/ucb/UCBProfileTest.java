package edu.rutgers.axs.ucb;

import static org.junit.Assert.*;

import org.junit.Test;

public class UCBProfileTest {

	@Test
	public void test() {
		System.out.println("\n testSimpleUpdate");
		double[][] cov ={{0.2,0.1},{0.1,0.5}};
		double[] mean = {0.4,0.7};
		double [][] X = {{0.2, 0.8}};
		double [] Y = {1.035};
		UCBProfile profile = new UCBProfile(cov, mean);
		profile.updateProfile( X, Y);
		
		System.out.println(profile.mu[0]);
		System.out.println(profile.mu[1]);

		System.out.println(profile.Sigma[0][0]);
		System.out.println(profile.Sigma[0][1]);
		System.out.println(profile.Sigma[1][0]);
		System.out.println(profile.Sigma[1][1]);

		assert (Math.abs(profile.mu[0] - 0.528108) < 1e-5);
		assert (Math.abs(profile.mu[1] - 1.14838) < 1e-5);
		assert (Math.abs(profile.Sigma[0][0] - 0.161081) < 1e-5);
		assert (Math.abs(profile.Sigma[0][1] + 0.0362162) < 1e-5);
		assert (Math.abs(profile.Sigma[1][0] + 0.0362162) < 1e-5);
		assert (Math.abs(profile.Sigma[1][1] - 0.0232432) < 1e-5);

	}

}
