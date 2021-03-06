package edu.rpi;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.PrintStream;
import java.io.File;

import soot.PackManager;
import soot.SourceLocator;
import soot.Transform;
import soot.options.Options;
import edu.rpi.sflow.*;
import edu.rpi.reim.*;
import static com.esotericsoftware.minlog.Log.*;


public class SootInference {
	
	public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
		
		//prefer Android APK files// -src-prec apk
//        Options.v().set_src_prec(Options.src_prec_apk);
		
		//output as APK, too//-f J
//        Options.v().set_output_format(Options.output_format_none);
//        Options.v().set_output_format(Options.output_format_jimple);
//        Options.v().set_keep_line_number(true);

        // Exclude packages
        String[] excludes = new String[] {
            "android.annotation",
            "android.hardware",
            "android.support",
            "android.media", 
            "com.android",
            "android.bluetooth", 
            "android.media",
            "com.google",
            "com.yume.android",
            "com.squareup.okhttp",
            "com.crashlytics",
//            "com.nbpcorp.mobilead", // ad
//            "com.inmobi.androidsdk", //ad
//            "com.millennialmedia", //ad
//            "com.admob",  //ad
//            "com.admarvel.android.ads",  // ad
//            "com.mopub.mobileads",  //ad
//            "com.medialets", // ad
            "com.slidingmenu",
            "com.amazon.inapp.purchasing",
            "com.loopj",
            "com.appbrain",
            "com.heyzap.sdk",
            "net.daum.adam.publisher",
            "twitter4j.",
            "org.java_websocket",
            "org.acra",
            "org.apache"
        };
        List<String> exclude = new ArrayList<String>(Arrays.asList(excludes));
        Options.v().set_exclude(exclude);

        set(LEVEL_DEBUG);


        InferenceTransformer reimTransformer = new ReimTransformer();
        InferenceTransformer sflowTransformer = new SFlowTransformer();
        PackManager.v().getPack("jtp").add(new Transform("jtp.reim", reimTransformer));
        PackManager.v().getPack("jtp").add(new Transform("jtp.sflow", sflowTransformer));

		soot.Main.main(args);

        info(String.format("%6s: %14d", "size", AnnotatedValueMap.v().size()));
        info(String.format("%6s: %14f MB", "free", ((float) Runtime.getRuntime().freeMemory()) / (1024*1024)));
        info(String.format("%6s: %14f MB", "total", ((float) Runtime.getRuntime().totalMemory()) / (1024*1024)));

        String outputDir = SourceLocator.v().getOutputDir();

        boolean needTrace = !(System.getProperty("noTrace") != null);

        System.out.println("INFO: Solving Reim constraints:  " + reimTransformer.getConstraints().size() + " in total...");
        ConstraintSolver cs = new SetbasedSolver(reimTransformer, false);
        Set<Constraint> errors = cs.solve();
        try {
            PrintStream reimOut = new PrintStream(outputDir + File.separator + "reim-constraints.log");
            for (Constraint c : reimTransformer.getConstraints()) {
                reimOut.println(c);
            }
            reimOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Constraint c : errors)
            System.out.println(c);
        System.out.println("INFO: Finish solving Reim constraints. " + errors.size() + " error(s)");

        try {
            PrintStream reimOut = new PrintStream(outputDir + File.separator + "reim-result.jaif");
            reimTransformer.printJaif(reimOut);
            //reimTransformer.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("INFO: Solving SFlow constraints:  " + sflowTransformer.getConstraints().size() + " in total...");
        ConstraintSolver sflowSolver = new SFlowConstraintSolver(sflowTransformer, needTrace);
        errors = sflowSolver.solve();
        try {
            PrintStream sflowOut = new PrintStream(outputDir + File.separator + "sflow-constraints.log");
            for (Constraint c : sflowTransformer.getConstraints()) {
                sflowOut.println(c);
            }
            sflowOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println();
        for (Constraint c : errors)
            System.out.println(c + "\n");
        System.out.println("INFO: Finish solving SFlow constraints. " + errors.size() + " error(s)");
        try {
            PrintStream sflowOut = new PrintStream(outputDir + File.separator + "sflow-result.jaif");
            sflowTransformer.printJaif(sflowOut);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("INFO: Annotated value size: " + AnnotatedValueMap.v().size());
		
        long endTime   = System.currentTimeMillis();
        System.out.println("INFO: Total running time: " + ((float)(endTime - startTime) / 1000) + " sec");
	}
}
