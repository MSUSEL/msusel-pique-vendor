/**
 * MIT License
 *
 * Copyright (c) 2021 Montana State University Software Engineering Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package piqueVendor.runnable;

public class Wrapper {
    public static void main(String[] args) {

        try {
            if (args == null || args.length < 1) {
                throw new IllegalArgumentException("Incorrect input parameters given. Be sure to include " +
                    "\n\t(0) The parameter to specify derivation (-d) or evaluation (-e)," +
                    "\n\t(1) (optional) Path to config file. See the config.properties file in src/test/resources/config for an example.");
            }

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--version":
                    case "-v":
                        System.out.println("PIQUE-vendor v1.0");
                        break;
                    case "--derive-model":
                    case "-d":
                        //kick off new Deriver
                        if (args.length > i + 1) {
                            new QualityModelDeriver(args[i + 1]);
                        } else {
                            new QualityModelDeriver();
                        }

                        i++; //properties file is read as input, need to increment i to jump past it to the next argument
                        break;
                    case "--evaluate-model":
                    case "-e":
                        //kick off model assessment
                        if (args.length > i + 1) {
                            new SingleProjectEvaluator(args[i + 1]);
                        } else {
                            new SingleProjectEvaluator();
                        }

                        i++;
                        break;
                    case "--help":
                    case "-h":
                    case "":
                        System.out.println("Run the jar file with the --derive-model (-d) to derive a quality model. ");
                        System.out.println("\t\tModel derivation involves populating a model with proper edge weights, threshold values, and structure" +
                            " to prepare for a model evaluation");
                        System.out.println("Run the jar file with the --evaluate-model (-e) to evaluate a quality model");
                        System.out.println("\t\tModel evaluation involves executing the model on a system under analysis to generate quality scores.");
                        break;
                    default:
                        System.out.println("System arguments not recognized, try --help or -h");

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
