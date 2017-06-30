/*
 * TODO LIST:
 * - Vertical migration/behaviour
 * - Parallelisation?
 *      + Threads using fork/join 
 *        http://www.oracle.com/technetwork/articles/java/fork-join-422606.html
 *        http://tutorials.jenkov.com/java-util-concurrent/java-fork-and-join-forkjoinpool.html
 *        (16 cores per cluster node)
 *      + MPI e.g. MPJ?
 */
package particle_track;

import java.io.*;
import java.util.*;

//import java.util.Collection;
//import java.util.List;
//import java.util.ArrayList;
//import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
//import java.lang.InterruptedException;

import edu.cornell.lassp.houle.RngPack.*;
//import static particle_track.IOUtils.countLines;

/**
 *
 * @author tomdude
 */
public class Particle_track {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // TODO code application logic here
    
        System.out.println("Starting particle tracking program\n");
        Date date = new Date();
        // display time and date using toString()
        System.out.println(date.toString());
       
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        System.out.println("Max heap " + heapMaxSize);
        
        //System.out.println(new Date().toString());
        long startTime = System.currentTimeMillis();
        
        RanMT ran = new RanMT(System.currentTimeMillis());
           
        System.out.println("Reading in data\n");
        
        System.out.println(System.getProperty("user.dir"));
           
        //RunProperties rp = new RunProperties("model_setup.properties");
        RunProperties rp = new RunProperties(args[0]); // first (and only?) cmd line arg is properties filename e.g. model_setup.properties
        // Use this instead of previous to create runProps from CMD line args
        //RunProperties runProps = new RunProperties(args); 
        
        int[] startDate = dateIntParse(rp.start_ymd);   
        ISO_datestr currentIsoDate = new ISO_datestr(startDate[0],startDate[1],startDate[2]);
        int[] endDate = dateIntParse(rp.end_ymd);   
        ISO_datestr endIsoDate = new ISO_datestr(endDate[0],endDate[1],endDate[2]);
        
        int numberOfDays = endIsoDate.getDateNum() - currentIsoDate.getDateNum() + 1;
                
        // Print all main arguments
        System.out.printf("-----------------------------------------------------------\n");
        System.out.printf("Location           = %s\n",rp.location);
        System.out.printf("Habitat            = %s\n",rp.habitat);
        System.out.printf("N_parts/site       = %d\n",rp.nparts);
        System.out.printf("hydromod dt (s)    = %f\n",rp.dt);
        System.out.printf("hydromod rec/file  = %d\n",rp.recordsPerFile+1);
        System.out.printf("stepsperstep       = %d\n",rp.stepsPerStep);
        System.out.printf("firstfile          = %d\n",rp.start_ymd);
        System.out.printf("lastfile           = %d\n",rp.end_ymd);
        System.out.printf("Simulated dur. (d) = %f\n",(double)numberOfDays);
        System.out.printf("Simulated dur. (s) = %f\n",(double)numberOfDays*86400);
        //System.out.printf("Simulated dur. (s) = %f\n",rp.dt*rp.recordsPerFile*(rp.lastday-rp.firstday+1));
        System.out.printf("RK4                = %s\n",rp.rk4);
        System.out.printf("Vertical behaviour = %d\n",rp.behaviour);        
        System.out.printf("Viable time (h)    = %f\n",rp.viabletime);
        System.out.printf("Viable time (d)    = %f\n",rp.viabletime/24.0);
        System.out.printf("Threshold distance = %d\n",rp.thresh);
        System.out.printf("Diffusion          = %f\n",rp.D_h);
        System.out.printf("-----------------------------------------------------------\n");    

       
        
        // --------------------------------------------------------------------------------------
        // File reading and domain configuration
        // --------------------------------------------------------------------------------------
        // Set the directories required to run the model
        //String[] dirList = IOUtils.setDataDirectories(location, cluster);
//        String basedir = dirList[0];
//        String sitedir = dirList[2];
//        String datadir = dirList[3];
//        String datadir2 = dirList[4];
        
                
        double[][] nodexy = new double[rp.M][2];
        double[][] uvnode = new double[rp.N][2];
        double[][] bathymetry = new double[rp.N][1];
        double[][] sigvec = new double[rp.N][1];
        int[][] trinodes = new int[rp.N][3];
        int[][] neighbours = new int[rp.N][3];
        
        uvnode = IOUtils.readFileDoubleArray(rp.datadir2+"uvnode.dat",rp.N,2," ",true); // the centroids of the elements
        nodexy = IOUtils.readFileDoubleArray(rp.datadir2+"nodexy.dat",rp.N,2," ",true);
        trinodes = IOUtils.readFileIntArray(rp.datadir2+"trinodes.dat",rp.N,3," ",true); // the corners of the elements
        neighbours = IOUtils.readFileIntArray(rp.datadir2+"neighbours.dat",rp.N,3," ",true);
        bathymetry = IOUtils.readFileDoubleArray(rp.datadir2+"bathymetry.dat",rp.N,1," ",true);
        sigvec = IOUtils.readFileDoubleArray(rp.datadir2+"sigvec.dat",rp.N,1," ",true);
        
        // Create a 1d array of the sigma layer depths
        double[] sigvec2 = new double[sigvec.length];
        for (int i = 0; i < sigvec.length; i++)
        {
            sigvec2[i] = sigvec[i][0];
        }
        System.out.println("sigvec2 "+sigvec2[0]+" "+sigvec2[1]+" "+sigvec2[2]+" ....");
        
        // reduce node/element IDs in files generated by matlab by one (loops start at zero, not one as in matlab)
        for (int i = 0; i < rp.N; i++)
        {
            //System.out.println(bathymetry[i][0]);
            for (int j = 0; j < 3; j++)
            {
                trinodes[i][j]--;
                //System.out.printf("%d ",trinodes[i][j]);
                if (neighbours[i][j]>0)
                {
                    neighbours[i][j]--;
                }             
            }
            //System.out.printf("\n");          
        }
        int[] allelems = new int[uvnode.length];
        for (int j = 0; j < uvnode.length; j++)
        {
            allelems[j] = j;
        }

       
        double subStepDt=rp.dt/(double)rp.stepsPerStep; // number of seconds per substep
        double dev_perstep = Math.pow(0.1,subStepDt);
        System.out.println("Particle subStepDt = "+subStepDt+" dev_perstep = "+dev_perstep);
        System.out.println("behaviour = "+rp.behaviour);

        // --------------------------------------------------------------------------------------
        // Creating initial particle array
        // --------------------------------------------------------------------------------------
        // load array of start node IDs (as stored by matlab)
        double startlocs[][] = new double[10][3];
        double endlocs[][] = new double[10][3];
        double open_BC_locs[][] = new double[1][3];
 
        startlocs = IOUtils.setupStartLocs(rp.sitefile,rp.sitedir);
        //startlocs = IOUtils.setupStartLocs(rp.location,rp.habitat,rp.basedir);
        endlocs = IOUtils.setupEndLocs(rp.habitat,rp.sitedir,startlocs);
        open_BC_locs = IOUtils.setupOpenBCLocs(rp.location,rp.datadir2); 
               
        int nparts_per_site=rp.nparts;
        int nTracksSavedPerSite=Math.min(1,nparts_per_site);
        int nparts=rp.nparts*startlocs.length; 
         
        for (int i = 0; i < startlocs.length; i++)
        {
            startlocs[i][0]--;
            //System.out.println(startlocs[i][0]+" "+startlocs[i][1]+" "+startlocs[i][2]);
        }
        
        // array to save source, destination, and transport time for each particle
        int[][] particle_info= new int[nparts][3];
        double[][] settle_density = new double[nparts][1];
        
        double[] xstart = new double[nparts];
        double[] ystart = new double[nparts];
        int[] startElem = new int[nparts];
        int[] startid = new int[nparts];
        System.out.println("nparts = "+nparts);
        //System.out.println("hello");
        for (int i = 0; i < nparts; i++)
        {
            //startid[i]=(int)(startlocs.length*ran.raw());
            startid[i]=i%startlocs.length;
            xstart[i]=startlocs[startid[i]][1];
            ystart[i]=startlocs[startid[i]][2];

            // If start location is a boundary location it is not actually in the mesh/an element, so set
            // new particle location to centre of nearest element.
            int closest=Particle.nearestCentroid(xstart[i],ystart[i],uvnode);
            startElem[i]=Particle.whichElement(xstart[i],ystart[i],allelems,nodexy,trinodes);
            if (startElem[i]<0)
            {
                xstart[i]=uvnode[closest][0];
                ystart[i]=uvnode[closest][1];
                startElem[i]=closest;
            }
            //System.out.printf("start location %d = %d %.4e %.4e %d %d\n",i,(int)startlocs[startid[i]][0],xstart[i],ystart[i],closest,startElem[i]);
        }

        // --------------------------------------------------------------------------------------
        // Setup particles
        // --------------------------------------------------------------------------------------
        List<Particle> particles = new ArrayList<Particle>(nparts);
        for (int i = 0; i < nparts; i++)    
        {
            particles.add(new Particle(xstart[i],ystart[i],startid[i],i,rp.mortalityRate));
            particle_info[i][0]=startid[i];//(int)startlocs[startid[i]][0];
            particles.get(i).setElem(startElem[i]);
            // if information provided, set release time
            switch(rp.releaseScenario)
            {
                // integer to switch release scenario
                // 0 all at time zero
                // 1 tidal release (evenly over first 24 hours)
                // 2 continuous release (1 per hour per site)
                // 3 continuous release (5 per hour per site)
                // 4 continuous release (10 per hour per site)
                // 5 continuous release (20 per hour per site)
                // 10 defined release times
                case 0: 
                    particles.get(i).setReleaseTime(0);
                    break;
                case 1: 
                    particles.get(i).setReleaseTime((i/startlocs.length)%25);
                    break;
                case 2: 
                    particles.get(i).setReleaseTime(Math.floor(i/startlocs.length));
                    break;
                case 3: 
                    particles.get(i).setReleaseTime(Math.floor(i/(5*startlocs.length)));
                    break;
                case 4: 
                    particles.get(i).setReleaseTime(Math.floor(i/(10*startlocs.length)));
                    break;
                case 5: 
                    particles.get(i).setReleaseTime(Math.floor(i/(20*startlocs.length)));
                    break;
                case 10: 
                    particles.get(i).setReleaseTime(startlocs[startid[i]][3]);
                    break;
            }
            //System.out.println("Particle "+i+" (release site "+particles[i].getStartID()+") release time: "+particles[i].getReleaseTime());
             // If provided, set particle depth
            if (startlocs[startid[i]].length > 4 && rp.setDepth == true)
            {
                particles.get(i).setZ(startlocs[startid[i]][4]);
            }
        }
        System.out.println("particles.length = "+particles.size());        
        IOUtils.particleLocsToFile(particles,nparts,0,"particlelocations_start.out");

        // --------------------------------------------------------------------------------------
        // Particle data arrays for model output
        // --------------------------------------------------------------------------------------
        // an array to save the number of "particle-timesteps" in each cell
        double[][] pstepsImmature = new double[rp.N][2];
        double[][] pstepsMature = new double[rp.N][2];
        double[][] pstepsInst = new double[rp.N][2];
        if (rp.splitPsteps == false)
        {
            for (int i = 0; i < rp.N; i++)
            {
                pstepsImmature[i][0] = i;
                pstepsMature[i][0] = i;
                pstepsInst[i][0] = i;
            }
        }
        else if (rp.splitPsteps == true)
        {
            pstepsImmature = new double[rp.N][startlocs.length+1];
            pstepsMature = new double[rp.N][startlocs.length+1];
            pstepsInst = new double[rp.N][startlocs.length+1];
        
            for (int i = 0; i < rp.N; i++)
            {
                pstepsImmature[i][0] = i;
                pstepsMature[i][0] = i;
                pstepsInst[i][0] = i;
                for (int j = 1; j < startlocs.length+1; j++)
                {
                    pstepsImmature[i][j] = 0;
                    pstepsMature[i][j] = 0;
                    pstepsInst[i][j] = 0;
                }
            }
        }
        
        
        
        /**
         * OUTSOURCE THIS METHOD!!!
         */
        
        // A list of times at which to print particle locations to file 
        // (hours from start of simulation)
        double[][] dumptimes = new double[10][1];
        double[] dumptimes2 = new double[dumptimes.length];
        System.out.println("Attempting to read dumptimes.dat");
        File file = new File("./dumptimes.dat");
        int nDumps = 0;
        try
        {
            nDumps = IOUtils.countLines(file);
            System.out.println("lines = "+nDumps);
        }
        catch (Exception e)
        {
            System.out.println("Cannot open ./dumptimes.dat");
        }
        if (nDumps > 0)
        {
            dumptimes = IOUtils.readFileDoubleArray("./dumptimes.dat",nDumps,1," ",true);
            dumptimes2 = new double[dumptimes.length];
            for (int i = 0; i < dumptimes.length; i++)
            {
                dumptimes2[i] = dumptimes[i][0];
            }
            System.out.println("dumptimes2 "+dumptimes2[0]+" "+dumptimes2[1]+" "+dumptimes2[2]+" ....");
        } 
        else
        {
            System.out.println("No file dumptimes.dat:\n"
                    + "- only cumulative element counts produced\n"
                    + "- only start/end particlelocations (and max 100 tracks/site) produced");
        }
        
        
        
        
        
        
        // --------------------------------------------------------------------------------------
        // Final setup bits
        // --------------------------------------------------------------------------------------
        System.out.println("Starting time loop");

        int[] searchCounts= new int[5];

        double minDistTrav=10000000;
        double maxDistTrav=0;

        int stepcount=0;
        int calcCount=0;
        double time=0;
                
        int printCount=0;

        int nfreeparts = 0;
        int nViable = 0;
        int nBoundary = 0;
        int nSettled = 0;
        
        //String filenums = "";
       
        // --------------------------------------------------------------------------------------
        // Start time loop
        // --------------------------------------------------------------------------------------
        
        
        //for (int fnum = rp.firstday; fnum <= rp.lastday; fnum++)
        for (int fnum = 0; fnum < numberOfDays; fnum++)
        {
            
            for (int dayQuarter = 1; dayQuarter <= 4; dayQuarter++)
            // alternatively, run loop backwards
            //for (int day = lastday; day >= firstday; day--)
            {
                System.out.printf(currentIsoDate.getDateStr());
                long currTime = System.currentTimeMillis();    
                System.out.printf("\n------ Day %d (%s) - Quarter %d - Stepcount %d (%f hrs)  - Runtime %f s ------ \n", 
                        fnum+1, currentIsoDate.getDateStr(), dayQuarter, stepcount, time, (currTime - startTime) / 1000.0);
                //System.out.printf("\nfile %d - time %fsecs (%fhrs) \n",fnum,stepcount*subStepDt*rp.stepsPerStep,time);

                // clear any old data
                //clear FVCOM1
                // load the new data file. this puts variables straight into the
                // workspace
                //int depthLayers = 10;       

                String ufile = "";
                String vfile = "";
                String elfile = "";

                double[][] u = new double[rp.recordsPerFile][rp.N*rp.depthLayers];
                double[][] v = new double[rp.recordsPerFile][rp.N*rp.depthLayers];
                //double[][] el = new double[recordsPerFile][N*depthLayers];
                //double[][] u1 = new double[recordsPerFile][N*depthLayers];
                //double[][] v1 = new double[recordsPerFile][N*depthLayers];
                //double[][] el1 = new double[recordsPerFile][N*depthLayers];

                // maintain backward compatability with previous number reading
//                filenums = ""+fnum;
//                // replace filenums in its entirety               
//                if (rp.location.equalsIgnoreCase("minch_continuous") || rp.location.equalsIgnoreCase("minch_jelly"))
//                {
//                    filenums = br.readLine();
//                } 
                //System.out.println("t=0 Reading t: "+filenums);
                //ufile = rp.datadir+"u_"+filenums+".dat";
                //vfile = rp.datadir+"v_"+filenums+".dat";
                // Use Mike's file reading method to avoid use of the filelist for reading in
                ufile = rp.datadir+"u_" + currentIsoDate.getDateStr() + "_" + dayQuarter + ".dat";
                vfile = rp.datadir+"v_" + currentIsoDate.getDateStr() + "_" + dayQuarter + ".dat";
                //String viscfile = datadir+"\\viscofm_"+fnum+".dat";
                //elfile = datadir+"el_"+filenums+".dat";
                //System.out.println(ufile+" "+vfile+" "+elfile);
                u = IOUtils.readFileDoubleArray(ufile,rp.recordsPerFile,rp.N*rp.depthLayers," ",true);
                v = IOUtils.readFileDoubleArray(vfile,rp.recordsPerFile,rp.N*rp.depthLayers," ",true);
                //double[][] viscofm = readFileDoubleArray(viscfile,recordsPerFile,N*10," ",false);
                //el = readFileDoubleArray(elfile,recordsPerFile,M*depthLayers," ",false);
                //double[][] sal = readFileDoubleArray(sfile,recordsPerFile,M*10," ",false);


                // set an initial tide state
                String tideState = "flood";

                System.out.println("Free particles    = "+nfreeparts);
                System.out.println("Viable particles  = "+nViable);
                System.out.println("Arrival count     = "+nSettled);
                System.out.println("Boundary exits    = "+nBoundary);

                // default, run loop forwards
                // ---- LOOP OVER ENTRIES IN THE HYDRO OUTPUT ------------------------
                for (int tt = 0; tt <= rp.recordsPerFile-2; tt++)
                // alternatively, run loop backwards
                //for (int tt = lasttime; tt >= firsttime; tt--)
                {
                    //System.out.printf("--------- TIME %d ----------\n",tt);
                    System.out.printf("%d ",tt+1);

                    boolean debug = false;
                    if (debug==true)
                        {
                            IOUtils.particleLocsToFile(particles,nparts,0,"particlelocations_t"+tt+".out");
                        }

                    // ---- INTERPOLATE BETWEEN ENTRIES IN THE HYDRO OUTPUT ------------------------
                    for (int st = 0; st < rp.stepsPerStep; st++)
                    {

                        //System.out.print(",");
                        //System.out.println("nfreeparts = "+nfreeparts);
                        for (int i = 0; i < nparts; i++)
                        {
                            // Set particles free once they pass thier defined release time (hours)
                            if (particles.get(i).getFree()==false)
                            {
                                if (time > particles.get(i).getReleaseTime())
                                {
                                    particles.get(i).setFree(true);
                                    //System.out.println("Particle "+i+" released from "+particles[i].getLocation()[0]+" "+particles[i].getLocation()[1]+" at t="+time);
                                    nfreeparts++;
                                }
                            }
                            if (particles.get(i).getFree()==true)
                            {    
                                particles.get(i).increaseAge(subStepDt/3600.0); // particle age in hours
                                //System.out.printf("PARTICLE %d \n",i);
                                if (particles.get(i).getArrived()==false)
                                {
                                    //System.out.println("particle able to move");
                                    int elemPart = particles.get(i).getElem();
                                    //System.out.printf("%d\n",elemPart);
                                    // set and get the DEPTH layer for the particle based on tide state
        //                            if (tt>0)
        //                            {
        //                                if (el[tt][trinodes[elemPart][0]]>el[tt-1][trinodes[elemPart][0]])
        //                                {
        //                                    particles[i].setDepthLayer(behaviour,"flood");
        //                                } else {
        //                                    particles[i].setDepthLayer(behaviour,"ebb");
        //                                }
        //                            }
                                    // set depth layer based on fixed depth in metres
                                    particles.get(i).setLayerFromDepth(bathymetry[elemPart][0],sigvec2);

                                    int dep = particles.get(i).getDepthLayer();
                                    //System.out.println("Depth ="+dep);

                                    // Find the salinity in the neighbourhood of the particle (used to compute instantaneous mortality rate).
                                    // This is stored at NODES as opposed to ELEMENT CENTROIDS.
                                    // So need to get the value from each of the corners and calculate 
                                    // a value at the particle location (similar method to getting velocity from nearest centroids).
                                    if (st == 0)
                                    {
                                        double salinity = 0;
                                        double mort = 0;
        //                                if (calcMort == true)
        //                                {
        //                                    salinity = particles[i].salinity(tt,sal,trinodes);
        //                                    particles[i].setMortRate(salinity);
        //                                }
                                        particles.get(i).setDensity();
                                    }

                                    double advectStep[] = new double[2];
        //                            System.out.printf("ADVECT: Euler=[%.3e,%.3e] RK4=[%.3e,%.3e]\n",
        //                                    advectStep[0],advectStep[1],advectStep2[0],advectStep2[1]);
                                    if (rp.rk4==true)
                                    {
                                        advectStep = particles.get(i).rk4Step(u, v, 
                                            neighbours, uvnode,  nodexy, trinodes, allelems,
                                            tt, st, subStepDt, rp.stepsPerStep, rp.depthLayers);   
                                    }
                                    else
                                    {
                                        System.err.println("Euler step not calculated --- edit method to work with 7 row velocities");
    //                                    advectStep = particles[i].eulerStepOld(u, v, u1, v1, neighbours, uvnode, 
    //                                        tt, st, subStepDt, stepsPerStep, recordsPerFile, fnum, lastday, depthLayers, 
    //                                        spatialInterpolate, timeInterpolate);
                                    }

                                    // Reverse velocities if running backwards
                                    if (rp.backwards == true)
                                    {
                                        advectStep[0] = -advectStep[0];
                                        advectStep[1] = -advectStep[1];
                                    }

                                    // 3. Calculate diffusion (random walk step)
        //                            if (variableDiff==true)
        //                            {
        //                                D_h=1000*viscofm[tt][elemPart*10+dep];
        //                            }

                                    //rand('twister',sum(100*clock)); %resets it to a different state each time.
                                    double diff_X = Math.sqrt(6*rp.D_h*subStepDt/(double)rp.stepsPerStep);
                                    double diff_Y = Math.sqrt(6*rp.D_h*subStepDt/(double)rp.stepsPerStep);
                                    double[] behave_uv = particles.get(i).behaveVelocity(rp.behaviour);
                                    // 4. update particle location
                                    //double ran1 = BoxMueller.bmTransform(ran.raw(),ran.raw());
                                    //double ran2 = BoxMueller.bmTransform(ran.raw(),ran.raw());
                                    double ran1 = 2.0*ran.raw()-1.0;
                                    double ran2 = 2.0*ran.raw()-1.0;
                                    //System.out.println("D_h = "+D_h+" diff_X = "+diff_X+" diff_Y "+diff_Y+" ran1 = "+ran1+" ran2 = "+ran2);
                                    //System.out.println("Distances travelled: X "+subStepDt*water_U+" "+diff_X*ran1+" Y "+subStepDt*water_U+" "+diff_Y*ran2);

                                    double newlocx=particles.get(i).getLocation()[0]+advectStep[0]+subStepDt*behave_uv[0]+diff_X*ran1; // simplest possible "Euler"
                                    double newlocy=particles.get(i).getLocation()[1]+advectStep[1]+subStepDt*behave_uv[1]+diff_Y*ran2;

                                    //System.out.println("Old = ("+particles[i].getLocation()[0]+", "+particles[i].getLocation()[1]+") --- New = ("+newlocx+", "+newlocy+")");

                                    // find element containing particle and update seach counts for diagnosis
                                    int[] c = particles.get(i).findContainingElement(newlocx, newlocy, elemPart, 
                                            nodexy, trinodes, neighbours, allelems);
                                    int whereami = c[0];
                                    for (int j = 0; j < 5; j++)
                                    {
                                        searchCounts[j] += c[j+1];
                                    }

                                    // if particle is within the mesh, update location normally and save the distance travelled
                                    if (whereami != -1)
                                    {
                                        double distTrav = Math.sqrt((particles.get(i).getLocation()[0]-newlocx)*(particles.get(i).getLocation()[0]-newlocx)+
                                                (particles.get(i).getLocation()[1]-newlocy)*(particles.get(i).getLocation()[1]-newlocy));
                                        if (distTrav>maxDistTrav)
                                        {
                                            maxDistTrav=distTrav;
                                        }
                                        if (distTrav<minDistTrav)
                                        {
                                            minDistTrav=distTrav;
                                        }
                                        particles.get(i).setLocation(newlocx,newlocy);
                                        particles.get(i).setElem(whereami);
                                        //System.out.printf("** MOVED **, new elem = %d (dist = %f)\n",particles[i].getElem(),Math.sqrt((newlocx-uvnode[particles[i].getElem()][0])*(newlocx-uvnode[particles[i].getElem()][0])+(newlocy-uvnode[particles[i].getElem()][1])*(newlocy-uvnode[particles[i].getElem()][1])));
                                    }

                                    // if particle has skipped out of the model domain, place it at the nearest element centroid
                                    if (whereami == -1)
                                    {
                                        int closest=Particle.nearestCentroid(particles.get(i).getLocation()[0],particles.get(i).getLocation()[1],uvnode);
                                        //fprintf('x%d',closest);
                                        particles.get(i).setLocation(uvnode[closest][0],uvnode[closest][1]);
                                        particles.get(i).setElem(closest);
                                    }

                                    // set particle to become able to settle after a predefined time
                                    if (particles.get(i).getAge()>rp.viabletime && particles.get(i).getViable()==false)
                                    {
                                        //System.out.println("Particle became viable");                                  
                                        particles.get(i).setViable(true);
                                        nViable++;
                                    }

                                    // Make additions to the element presence counts (PSTEPS)

                                    double d = 1;
                                    if (rp.pstepsIncMort ==true)
                                    {
                                        d=particles.get(i).getDensity();
                                        //System.out.println("density = "+d+" mortRate = "+particles[i].getMortRate());
                                    }
                                    if (particles.get(i).getViable()==true)
                                    {
                                        if (rp.splitPsteps==false)
                                        {
                                            pstepsMature[elemPart][1]+=d*(subStepDt/3600)*1.0/rp.stepsPerStep;
                                        }
                                        else
                                        {
                                            pstepsMature[elemPart][particles.get(i).getStartID()+1]+=d*(subStepDt/3600)*1.0/rp.stepsPerStep;
                                        }
                                    } 
                                    else 
                                    {
                                        //System.out.println("Printing to pstepsImmature");
                                        if (rp.splitPsteps==false)
                                        {
                                            pstepsImmature[elemPart][1]+=d*(subStepDt/3600)*1.0/rp.stepsPerStep;
                                        }
                                        else
                                        {
                                            pstepsImmature[elemPart][particles.get(i).getStartID()+1]+=d*(subStepDt/3600)*1.0/rp.stepsPerStep;
                                        }                                          
                                    }

                                    // check whether the particle has gone within a certain range of one of the boundary nodes
                                    // (make it settle there, even if it is inviable)
                                    for (int loc = 0; loc < open_BC_locs.length; loc++)
                                        {
                                            double dist = Math.sqrt((particles.get(i).getLocation()[0]-open_BC_locs[loc][1])*(particles.get(i).getLocation()[0]-open_BC_locs[loc][1])+
                                                    (particles.get(i).getLocation()[1]-open_BC_locs[loc][2])*(particles.get(i).getLocation()[1]-open_BC_locs[loc][2]));
                                            if (dist < 1500)
                                            {
                                                //System.out.printf("Boundary stop: %d at %d\n",i,loc);
                                                particles.get(i).setArrived(true);
                                                particle_info[i][1] = -loc;//(int)startlocs[loc][0];
                                                particle_info[i][2] = (int)particles.get(i).getAge();//((day-firstday)*24+tt);
                                                nBoundary++;
                                                break;

                                            }
                                        }

                                    // if able to settle, is it close to a possible settlement
                                    // location?
                                    //System.out.println("Patricle age = "+particles.get(i).getAge()+" Viabletime/3600 = "+viabletime/3600.0+" viable = "+particles.get(i).getViable());
                                    if (particles.get(i).getViable()==true)
                                    {
                                        //System.out.println(particles[i].getViable());

                                        for (int loc = 0; loc < endlocs.length; loc++)
                                        {
                                            double dist = Math.sqrt((particles.get(i).getLocation()[0]-endlocs[loc][1])*(particles.get(i).getLocation()[0]-endlocs[loc][1])+
                                                    (particles.get(i).getLocation()[1]-endlocs[loc][2])*(particles.get(i).getLocation()[1]-endlocs[loc][2]));
                                            if (dist < rp.thresh && particles.get(i).getSettledThisHour()==false)
                                            {
                                                //System.out.printf("settlement: %d at %d\n",i,loc);
                                                if (rp.endOnArrival==true)
                                                {
                                                    particles.get(i).setArrived(true);
                                                }
                                                particle_info[i][1] = loc;//(int)startlocs[loc][0];
                                                particle_info[i][2] = (int)time;//((day-firstday)*24+tt);
                                                settle_density[i][0] = particles.get(i).getDensity();
                                                nSettled++;
                                                particles.get(i).setSettledThisHour(true);
                                                break;
                                            }
                                        }    
                                    }
                                }
                            }
                        }
                        time+=subStepDt/3600.0;

                        // Dump particle locations to file at predfined times
                        if (nDumps > 0)
                        {
                            for (int ot = 0; ot < dumptimes.length; ot++)
                            {
                                if (time>dumptimes2[ot])
                                {
                                    System.out.println("print particle locations to file "+ot);
                                    IOUtils.particleLocsToFile1(particles,"particlelocations_"+ot+".out");
                                    for (int i = 0; i < particles.size(); i++)
                                    {
                                        pstepsInst[particles.get(i).getElem()][1]+=1;
                                    }
                                    IOUtils.writeDoubleArrayToFile(pstepsInst,"elementCounts_"+ot+".out");
                                    // Once recorded, set this value to be greater than simulation length
                                    dumptimes2[ot] = rp.dt*rp.recordsPerFile*numberOfDays*2;
                                }
                            }
                        }                    
                        // end of particle loop
                        calcCount++;
                    }
                    for (int i=0; i < particles.size(); i++)
                    {
                        particles.get(i).setSettledThisHour(false);
                    }
                    printCount++;
                    // Append particle locations for first nSites for plotting trajectories
                    IOUtils.particleLocsToFile(particles,startlocs.length*nTracksSavedPerSite,printCount,"particlelocations_all"+rp.suffix+".out");
                    stepcount++;
                }
                System.out.printf("\n");
            }
            currentIsoDate.addDay();
        }
        System.out.printf("\nelement search counts: %d %d %d %d %d\n",searchCounts[0],searchCounts[1],searchCounts[2],searchCounts[3],searchCounts[4]);
        System.out.printf("transport distances: min = %.4e, max = %.4e\n", minDistTrav, maxDistTrav);

        IOUtils.writeDoubleArrayToFile(pstepsImmature,"pstepsImmature"+rp.suffix+".out");
        IOUtils.writeDoubleArrayToFile(pstepsMature,"pstepsMature"+rp.suffix+".out");
        IOUtils.writeIntegerArrayToFile(particle_info,"particle_info"+rp.suffix+".out");
        IOUtils.writeDoubleArrayToFile(settle_density,"settle_density"+rp.suffix+".out");
        //IOUtils.particleLocsToFile(particles,nparts,0,"particlelocations"+suffix+".out");
        //IOUtils.writeDoubleArrayToFile(particle1Velocity,"particle1velocity"+suffix+".out");
        //IOUtils.writeDoubleArrayToFile(particle1Location,"particle1location"+suffix+".out");
        IOUtils.particleLocsToFile1(particles,"particlelocations_end"+rp.suffix+".out");
        
        long endTime = System.currentTimeMillis();
        System.out.println("Elapsed time = "+(endTime-startTime)/1000.0);
    }
    
    // work out the date from an integer in format YYYYMMDD - Mike Bedington method
    public static int[] dateIntParse(int ymd) {
        double start_ymd_mod = (double)(ymd);
        int startYear = (int)Math.floor(start_ymd_mod/10000);
        start_ymd_mod = start_ymd_mod - startYear*10000;
        int startMonth = (int)Math.floor(start_ymd_mod/100);
        start_ymd_mod = start_ymd_mod - startMonth*100;
        int startDay = (int)start_ymd_mod;
        
        int[] output = new int[]{startDay,startMonth,startYear};
        return output;
    }
    
    public static void memTest() {
        long heapSize = Runtime.getRuntime().totalMemory();
        System.out.println("Total heap memory " + heapSize);
        long heapFreeSize = Runtime.getRuntime().freeMemory();
        System.out.println("Free heap memory " + heapFreeSize);

    }
    
    public void setupOutput()
    {
    }

    public void writeOutput()
    { 
    }
}
