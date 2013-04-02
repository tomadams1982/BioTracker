/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package particle_track;

//import java.lang.Object;
////import ucar.nc2.dataset.*;
//import ucar.nc2.NCdumpW;
////import ucar.nc2.Attribute;
////import ucar.nc2.Netcdf;
//import ucar.nc2.NetcdfFile;
//import ucar.nc2.Variable;
//import ucar.nc2.iosp.IOServiceProvider;
//import ucar.ma2.*;
//
//import ucar.ma2.ArrayFloat;
//import ucar.ma2.InvalidRangeException;

import java.io.*;
//import java.util.Date;
import edu.cornell.lassp.houle.RngPack.*;
//import uk.ac.ed.s0676158.math.BoxMueller;

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
        
        //System.out.println(new Date().toString());
        long startTime = System.currentTimeMillis();
        
        RanMT ran = new RanMT(System.currentTimeMillis());
           
        System.out.println("Reading in data\n");
        
        /**
         * files are 
         * meshdata.nc:
         * uv_[3-31].nc: u, v
         * 
         * or
         * 
         * nodexy.dat, uvnode.dat, trinodes.dat, neighbours.dat
         * u_[3-31].dat
         * v_[3-31].dat
         */ 
        
        boolean lochfyne = false;
        
        String basedir = "C:\\Users\\sa01ta\\Documents\\particle_track\\";
        
        //String datadir = "May2010_mat";
        //String datadir = "dima_data\\azimuth3_v9b\\june_2011";
        //String datadir = "dima_data\\azimuth3_v9b\\FVCOM_2011_oct";
        String datadir = "dima_data\\FVCOM_2011_jun_9d";
        //String datadir = "dima_data\\FVCOM_2011_oct_9d";
        int N = 25071;
        int M = 14000;
        if (lochfyne==true)
        {
            datadir = "fvcom_output\\120511_1524_feb12wind\\output\\ptrack_dat";
            N = 1593;
            M = 943;
        }
        System.out.println("DATA DIRECTORY = "+datadir);
        
        
        
        
        //String meshfile = "D:\\dima_data\\May2010_mat\\meshdata.nc";
        double[][] nodexy = readFileDoubleArray("D:\\"+datadir+"\\nodexy.dat",M,2," ",true); // the original mesh nodes
        double[][] uvnode = readFileDoubleArray("D:\\"+datadir+"\\uvnode.dat",N,2," ",true); // the centroids of the elements
        int[][] trinodes = readFileIntArray("D:\\"+datadir+"\\trinodes.dat",N,3," ",true); // the corners of the elements
        int[][] neighbours = readFileIntArray("D:\\"+datadir+"\\neighbours.dat",N,3," ",true); // the neighbouring elements of each element
        //double[][] bathymetry = readFileDoubleArray("D:\\"+datadir+"\\bathymetry.dat",N,1," ",true); // the depth at each uvnode
        double[][] sigvec = readFileDoubleArray("D:\\"+datadir+"\\sigvec.dat",N,1," ",true);
//        double[] sigvec2 = new double[sigvec.length];
//        // for convenience make a 1d array of sigvec
//        for (int i = 0; i < sigvec.length; i++)
//        {
//            sigvec2[i] = sigvec[i][0];
//        }
        // reduce node/element IDs in files generated by matlab by one (loops start at zero, not one as in matlab)
        for (int i = 0; i < N; i++)
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
        
        // will this work directly? check netcdf file variables
        // TODO make function nc_varget -- same data as mesh.uvnodes
        // TODO actually need to interpolate using x,y and nv (list of corner nodes for each element)

        // setup farms and boundary
        //int tstart=1000;
        //int tmax=3000;
        int nparts=940;
        nparts=1;
        double dt=3600;
        
        int firstday=3;
        int lastday=31;
        int recordsPerFile=24;
        
        int stepsPerStep=200;
        
        // The threshold distance, closer than which particles are deemed to have settled.
        int thresh = 500;
        
        // Particles become viable when they are halway through their PLD. 
        // This is compared to individual particle age later.
        double viabletime = 0;
               
        // options for behaviour of particles - depth aspects are set at each
        // timestep by function
        /**
         * 1 - passive, stay on surface
         * 2 - passive, stay on bottom (layer 10)
         * 3 - passive, stay in mid layer (layer 5)
         * 6 - top during flood tides, mid during ebb (local)
         * 7 - mid during flood tides, bed during ebb (local)
         * 8 - top during flood tides, bed during ebb (local)
         */
        int behaviour=8;
        
        boolean timeInterpolate = false;
        boolean spatialInterpolate = false;
        double D_h = 0.1;
        
        if (args.length > 0)
        {
            try 
            {
                nparts=Integer.parseInt(args[0]);
                firstday=Integer.parseInt(args[1]);
                lastday=Integer.parseInt(args[2]);
                recordsPerFile=Integer.parseInt(args[3]);
                dt=Integer.parseInt(args[4]);
                stepsPerStep=Integer.parseInt(args[5]);
                thresh=Integer.parseInt(args[6]);
                viabletime=Integer.parseInt(args[7]);
                behaviour=Integer.parseInt(args[8]);
                if (args[9].equalsIgnoreCase("true"))
                {
                    timeInterpolate = true;
                }
                if (args[10].equalsIgnoreCase("true"))
                {
                    spatialInterpolate = true;
                }
                D_h = Double.parseDouble(args[11]);
                System.out.println("nparts = "+nparts+"\nsettlement threshold distance = "+thresh+"\nviabletime = "+viabletime+" D_h = "+D_h);
            }
            catch (IndexOutOfBoundsException e)
            {
                System.err.println("Incorrect number of input parameters, found "+args.length);
            }
        }
        
        String suffix = "";
        if (args.length == 13)
        {
            suffix = args[12];
        }
  
        if (lochfyne != true)
        {
            viabletime=(lastday-firstday+1)*recordsPerFile*dt/2;
            System.out.println("ignoring input viable time, set to "+viabletime);
        }
        
        int nrecords=(lastday-firstday+1)*recordsPerFile;
        int inviabletime=nrecords;
        // int nparts=20;
        // int tot_psteps=nparts*nrecords;

        //fprintf('----------------------------------------------------------------\n');
        
        dt=dt/(double)stepsPerStep;

        // default start location
        int s=1;

        // three different options for computing velocity
        // 1 - velocity at nearest element centroid
        // 2 - velocity from nearest 5 centroids (inverse-distance weighted)
        // 3 - velocity from element that particle is located within
        int veltype=3;

        System.out.println("behaviour = "+behaviour);

        // should we loops over start locations (=1) or over behaviour (=0)?
        //loopstart=0;

        // load array of start node IDs (as stored by matlab)
        
        // LORN configurations
        
        //double startlocs[][] = readFileDoubleArray(basedir+"lorncoast\\boundary_940pt.dat",940,3," ",true);
        //double startlocs[][] = readFileDoubleArray(basedir+"lorncoast\\boundary_with_interest.dat",966,3," ",true);
        //double startlocs[][] = readFileDoubleArray(basedir+"lorncoast\\boundary_with_search.dat",1252,3," ",true);
        //double startlocs[][] = readFileDoubleArray(basedir+"infrastructure\\portsmarinas_num.txt",41,3," ",true);
        double startlocs[][] = readFileDoubleArray(basedir+"infrastructure\\portsmarinas_search.dat",353,3," ",true);
        
        nparts=nparts*startlocs.length;        
        
        // FYNE configurations
        if (lochfyne==true)
        {
            startlocs = setupStartLocationsFyneFarms();
        }
        
        for (int i = 0; i < startlocs.length; i++)
        {
            startlocs[i][0]--;
            //System.out.println(startlocs[i][0]);
        }

        //nparts=startlocs.length;
        //int tot_psteps=nparts*nrecords;

        // an array to save the number of "particle-timesteps" in each cell
        int[][] psteps= new int[N][2];
        for (int i = 0; i < N; i++)
        {
            psteps[i][0] = i;
        }
        // array to save source, destination, and transport time for each particle
        int[][] particle_info= new int[nparts][3];
        
        double[] xstart = new double[nparts];
        double[] ystart = new double[nparts];
        int[] startElem = new int[nparts];
        int[] startid = new int[nparts];
        System.out.println("nparts = "+nparts);
        //System.out.println("hello");
        for (int i = 0; i < nparts; i++)
        {
            //System.out.printf("PARTICLE %d\n",i);

            //startid[i]=(int)(startlocs.length*ran.raw());
            startid[i]=i%startlocs.length;
            
            //startid[i]=697;
            
            //System.out.printf("%d", startlocs.length);
            //behaviour=6;
            //startid[i]=5;
            xstart[i]=startlocs[startid[i]][1];
            ystart[i]=startlocs[startid[i]][2];
            //System.out.printf("start location %d = %d %.4e %.4e\n",i,(int)startlocs[startid[i]][0],xstart[i],ystart[i]);

            // this boundary location is not actually in the mesh/an element, so set
            // new particle location to centre of nearest element.
            int closest=Particle.nearestCentroid(xstart[i],ystart[i],uvnode);

            xstart[i]=uvnode[closest][0];
            ystart[i]=uvnode[closest][1];
            
            startElem[i]=Particle.whichElement(xstart[i],ystart[i],allelems,nodexy,trinodes);
//            if (startElem[i]<0)
//            {
                //System.out.println(xstart[i]+" "+ystart[i]+" "+startElem[i]+" "+closest);
//            }
        }

        // setup particles
        Particle[] particles = new Particle[nparts];
        System.out.println("particles.length = "+particles.length);
        for (int i = 0; i < particles.length; i++)
        {
            particles[i] = new Particle(xstart[i],ystart[i]);
            particle_info[i][0]=startid[i];//(int)startlocs[startid[i]][0];
            particles[i].setElem(startElem[i]);
        }


        // ------------------- loop 2 = timestep ----------------------------
        //fprintf('Starting time loop\n');


        int count0=0;
        int count1=0;
        int count2=0;
        int count3=0;
        int count4=0;

        double minDistTrav=10000000;
        double maxDistTrav=0;

        int stepcount=0;
        int calcCount=0;
        double time=0;
        
        //int nfreeparts = 0;
        int nfreeparts = nparts;
        
        double[][] particle1Velocity = new double[nrecords*stepsPerStep][3];
        double[][] particle1Location = new double[nrecords*stepsPerStep][2];
        
        // default, run loop forwards
        for (int fnum = firstday; fnum <= lastday; fnum++)
        // alternatively, run loop backwards
        //for (int day = lastday; day >= firstday; day--)
        {
            System.out.printf("\nfile %d - time %fsecs (%fhrs) \n",fnum,stepcount*dt*stepsPerStep,time);
            
            // clear any old data
            //clear FVCOM1
            // load the new data file. this puts variables straight into the
            // workspace
            
            String ufile = "D:\\"+datadir+"\\u_"+fnum+".dat";
            String vfile = "D:\\"+datadir+"\\v_"+fnum+".dat";
            String elfile = "D:\\"+datadir+"\\el_"+fnum+".dat";
            double[][] u = readFileDoubleArray(ufile,recordsPerFile,N*10," ",false);
            double[][] v = readFileDoubleArray(vfile,recordsPerFile,N*10," ",false);
            double[][] el = readFileDoubleArray(elfile,recordsPerFile,M*10," ",false);
            //String ufile1 = "D:\\"+datadir+"\\u_"+(fnum+1)+".dat";
            //String vfile1 = "D:\\"+datadir+"\\v_"+(fnum+1)+".dat";
            //String elfile1 = "D:\\"+datadir+"\\el_"+(fnum+1)+".dat";
            //double[][] u1 = readFileDoubleArray(ufile1,recordsPerFile,N*10," ",false);
            //double[][] v1 = readFileDoubleArray(vfile1,recordsPerFile,N*10," ",false);
            //double[][] el1 = readFileDoubleArray(elfile1,recordsPerFile,M*10," ",false);

            
            int firsttime=0;
            int lasttime=recordsPerFile;
            
            // set an initial tide state
            String tideState = "flood";
            
            // default, run loop forwards
            for (int tt = firsttime; tt <= recordsPerFile-1; tt++)
            // alternatively, run loop backwards
            //for (int tt = lasttime; tt >= firsttime; tt--)
            {
                System.out.printf("%d ",tt);
        //             if (mod(tt,100)==0) 
        //                 fprintf('%d ',tt); 
        //             end
                
                if (nfreeparts < nparts)
                {
                    nfreeparts += startlocs.length;
                    System.out.println("\nTotal number of free particles now = "+nfreeparts);
                }
                
                //int dep=Particle.setParticleDepth(behaviour,tt,0,new double[10]);
                //uchunk=squeeze(u(tt,dep,:));
                //vchunk=squeeze(v(tt,dep,:));
                //velocities=[uchunk vchunk];
                
                boolean debug = false;
                if (debug==true)
                    {
                        particleLocsToFile(particles,"particlelocations_t"+tt+".out");
                    }
                
                nfreeparts = (int)Math.min(nparts,nfreeparts+Math.floor((double)nparts/25.0));
                for (int st = 0; st < stepsPerStep; st++)
                {
                    //System.out.print(",");
                    for (int i = 0; i < nfreeparts; i++)
                    {
                        particles[i].increaseAge(dt/3600.0);
                        //fprintf('PARTICLE %d\n',i);
                        if (particles[i].getArrived()==false)
                        {
                            //fprintf('%d',elemPart(i));
                            int elemPart = particles[i].getElem();
                            
                            // set the depth layer for the particle based on tide state
                            if (tt>0)
                            {
                                if (el[tt][trinodes[elemPart][0]]>el[tt-1][trinodes[elemPart][0]])
                                {
                                    particles[i].setDepthLayer(behaviour,"flood");
                                } else {
                                    particles[i].setDepthLayer(behaviour,"ebb");
                                }
                            }
                            
                            int dep = particles[i].getDepthLayer();
                            //System.out.println(dep);
                            
                            
                            // Set velocity array here first and fill with the right values. Need an array with velocity "now" 
                            // and one with velocity at "next" time step, to be populated before go to time interpolation bit 
                            // (which should happen last, after any possible spatial interpolation).
                            

                            double water_U = 0;
                            double water_V = 0;
                            
                            if (spatialInterpolate==false)
                            {
                                water_U = u[tt][elemPart*10+dep];
                                water_V = v[tt][elemPart*10+dep];
                                if (timeInterpolate == true)
                                {
                                    if (tt < recordsPerFile-1)
                                    {
                                        water_U = u[tt][elemPart*10+dep] + ((double)st/(double)stepsPerStep)*(u[tt+1][elemPart*10+dep]-u[tt][elemPart*10+dep]);
                                        water_V = v[tt][elemPart*10+dep] + ((double)st/(double)stepsPerStep)*(v[tt+1][elemPart*10+dep]-v[tt][elemPart*10+dep]);
                                    } 
                                    else
                                    {
                                        water_U = u[tt][elemPart*10+dep];
                                        water_V = v[tt][elemPart*10+dep];
                                        //water_U = u[tt][elemPart*10+dep] + ((double)st/(double)stepsPerStep)*(u1[0][elemPart*10+dep]-u[tt][elemPart*10+dep] );
                                        //water_V = v[tt][elemPart*10+dep] + ((double)st/(double)stepsPerStep)*(v1[0][elemPart*10+dep]-v[tt][elemPart*10+dep]);                             
                                    }
                                } 
                                
                            }
                            else if (spatialInterpolate == true)
                            {
                                // the values set just here are NOT used, just printed for comparison
                                water_U = u[tt][elemPart*10+dep];
                                water_V = v[tt][elemPart*10+dep];
                                //System.out.printf("Vel(element %d) = %.4f %.4f\n",elemPart,water_U,water_V);
                                
                                particles[i].setNrList(uvnode);
                                double[] vel = particles[i].velocityFromNearestList(tt,u,v);
                                water_U = vel[0];
                                water_V = vel[1];
                                if (timeInterpolate == true)
                                {
                                    double[] velplus1 = new double[2];
                                    if (tt < recordsPerFile-1)
                                    {
                                        velplus1 = particles[i].velocityFromNearestList(tt+1,u,v);
                                        
                                    }
                                    else
                                    {
                                        velplus1 = particles[i].velocityFromNearestList(tt,u,v);
                                        //velplus1 = particles[i].velocityFromNearestList(0,u1,v1);
                                    }
                                    water_U = vel[0] + ((double)st/(double)stepsPerStep)*(velplus1[0]-vel[0]);
                                    water_V = vel[1] + ((double)st/(double)stepsPerStep)*(velplus1[1]-vel[1]);
                                }
                            }
                            
                                  
                            
                            // below velocities used if running backwards
                            
                            //water_U = -water_U;
                            //water_V = -water_V;
                            
                            // save location information "start of timestep"
                            psteps[elemPart][1]++;

                            //[water_U,water_V]=calc_vel1(particles(i),[uchunk vchunk]);
                            // 3. Calculate diffusion    
                            //rand('twister',sum(100*clock)); %resets it to a different state each time.
                            double diff_X = Math.sqrt(6*D_h*dt/(double)stepsPerStep);// /(double)stepsPerStep);
                            double diff_Y = Math.sqrt(6*D_h*dt/(double)stepsPerStep);// /(double)stepsPerStep);    //+/- is random so direction doesn't matter
                            //diff_X=0; diff_Y=0;
                            double[] behave_uv = particles[i].behaveVelocity(behaviour);
                            // 4. update particle location
                            //double ran1 = BoxMueller.bmTransform(ran.raw(),ran.raw());
                            //double ran2 = BoxMueller.bmTransform(ran.raw(),ran.raw());
                            double ran1 = 2.0*ran.raw()-1.0;
                            double ran2 = 2.0*ran.raw()-1.0;
                            //System.out.println("D_h = "+D_h+" diff_X = "+diff_X+" diff_Y "+diff_Y+" ran1 = "+ran1+" ran2 = "+ran2);
                            //System.out.println("Distances travelled: X "+dt*water_U+" "+diff_X*ran1+" Y "+dt*water_U+" "+diff_Y*ran2);
                            double newlocx=particles[i].getLocation()[0]+dt*water_U+dt*behave_uv[0]+diff_X*ran1; // simplest possible "Euler"
                            double newlocy=particles[i].getLocation()[1]+dt*water_V+dt*behave_uv[1]+diff_Y*ran2;

                            //double newlocx=particles[i].getLocation()[0]+diff_X*ran1; // simplest possible "Euler"
                            //double newlocy=particles[i].getLocation()[1]+diff_Y*ran2;

                            if (i == 4)
                            {                               
                                particle1Velocity[calcCount][0]=time;
                                particle1Velocity[calcCount][1]=Math.sqrt(water_U*water_U+water_V*water_V);
                                particle1Velocity[calcCount][2]=elemPart;
                                //System.out.println(particle1Velocity[calcCount][0]+" "+particle1Velocity[calcCount][1]);
                                
                                particle1Location[calcCount][0]=time;
                                particle1Location[calcCount][1]=Math.sqrt(Math.pow(xstart[i]-newlocx,2.0)+Math.pow(ystart[i]-newlocy,2.0));
                            }
                            
                            
                            // search progressively further from previous element for new particle location
                            //fprintf('+');
                            int[] elems = new int[1];
                            elems[0] = elemPart;
                            int whereami=Particle.whichElement(newlocx,newlocy,elems,nodexy,trinodes);
                            count0=count0+1;
                            if (whereami==-1)
                            {
                                int[] elems0 = neighbours[elemPart];
                                count1++;
                                whereami=Particle.whichElement(newlocx,newlocy,elems0,nodexy,trinodes);
                                // if fails, look in nearest 10 (id numerical)
                                if (whereami==-1)
                                {
                                    //fprintf('a');
                                    //checkfirst
                                    count2=count2+1;
                                
                                    int[] elems1 = new int[10];
                                    for (int j = 0; j < 10; j++)
                                    {
                                        elems1[j] = Math.min(Math.max(elemPart-5+j,0),N-1);
                                    }
                                    whereami=Particle.whichElement(newlocx,newlocy,elems1,nodexy,trinodes);
                                    // if fails, look in nearest 500 (id numerical)
                                    if (whereami==-1)
                                    {
                                        //fprintf('b');
                                        //checkfirst
                                        count3=count3+1;
                                        
                                        int[] elems2 = new int[500];
                                        for (int j = 0; j < 500; j++)
                                        {
                                            elems2[j] = Math.min(Math.max(elemPart-250+j,0),N-1);
                                        }
                                        whereami=Particle.whichElement(newlocx,newlocy,elems2,nodexy,trinodes);
                                        // if this fails, look in all elements
                                        if (whereami==-1)
                                        {
                                            //fprintf('c');
                                            count4=count4+1;
                                            whereami=Particle.whichElement(newlocx,newlocy,allelems,nodexy,trinodes);
                                            //elemPart(i)=nearest_centroid(particles(i).x,particles(i).y,centroids);
                                        }
                                    }
                                }
                            }

                            // if particle is within the mesh, update location normally and save the distance travelled
                            if (whereami != -1)
                            {
                                double distTrav = Math.sqrt((particles[i].getLocation()[0]-newlocx)*(particles[i].getLocation()[0]-newlocx)+
                                        (particles[i].getLocation()[1]-newlocy)*(particles[i].getLocation()[1]-newlocy));
                                if (distTrav>maxDistTrav)
                                {
                                    maxDistTrav=distTrav;
                                }
                                if (distTrav<minDistTrav)
                                {
                                    minDistTrav=distTrav;
                                }
                                particles[i].setLocation(newlocx,newlocy);
                                particles[i].setElem(whereami);
                            }
                            
                            // if particle has skipped out of the model domain, place it at the nearest element centroid
                            if (whereami == -1)
                            {
                                int closest=Particle.nearestCentroid(particles[i].getLocation()[0],particles[i].getLocation()[1],uvnode);
                                //fprintf('x%d',closest);
                                particles[i].setLocation(uvnode[closest][0],uvnode[closest][1]);
                                particles[i].setElem(closest);
                            }

                            // set particle to become able to settle after a predefined time
                            //if ((day-firstday)*24+tt==viabletime)
                            if (particles[i].getAge()>viabletime/3600.0)
                            //if (time>viabletime)
                            {
                                particles[i].setViable(true);
                                //System.out.println(time+" "+particles[i].getAge());
                                //System.out.println("I can settle");
                            }

                            // if able to settle, is it close to a possible settlement
                            // location?
                            if (particles[i].getViable()==true)
                            {
                                //System.out.println(particles[i].getViable());
                                
                                for (int loc = 0; loc < startlocs.length; loc++)
                                {
                                    double dist = Math.sqrt((particles[i].getLocation()[0]-startlocs[loc][1])*(particles[i].getLocation()[0]-startlocs[loc][1])+
                                            (particles[i].getLocation()[1]-startlocs[loc][2])*(particles[i].getLocation()[1]-startlocs[loc][2]));
                                    if (dist < thresh)
                                    {
                                        //System.out.printf("settlement: %d at %d\n",i,loc);
                                        particles[i].setArrived(true);
                                        particle_info[i][1] = loc;//(int)startlocs[loc][0];
                                        particle_info[i][2] = (int)time;//((day-firstday)*24+tt);
                                        break;
                                    }
                                }    
                            }

                        }
                    }
                    time+=dt/3600.0;
//                    if ((day-firstday)*24+tt==viabletime && st==0)
//                    {
//                        System.out.println("print particle locations to file");
//                        particleLocsToFile(particles,"particlelocations"+thresh+"_"+viabletime+".out");
//                    }
                    
                    // end of particle loop
                    calcCount++;
                }
                for (int i = 0; i < 20; i++)
                    {
                        particleLocsToFile2(time,particles,i,"loc_"+i+".out");
                    }
                // print particle locations here - once every hour
                stepcount++;
            }
            System.out.printf("\n");

        }  
        System.out.printf("\nelement search counts: %d %d %d %d %d\n",count0,count1,count2,count3,count4);
        System.out.printf("transport distances: min = %.4e, max = %.4e\n", minDistTrav, maxDistTrav);

        writeIntegerArrayToFile(psteps,"psteps"+suffix+".out");
        writeIntegerArrayToFile(particle_info,"particle_info"+suffix+".out");
        particleLocsToFile(particles,"particlelocations"+suffix+".out");
        writeDoubleArrayToFile(particle1Velocity,"particle1velocity"+suffix+".out");
        writeDoubleArrayToFile(particle1Location,"particle1location"+suffix+".out");

        //System.out.println(new Date().toString());
        long endTime = System.currentTimeMillis();
        System.out.println("Elapsed time = "+(endTime-startTime)/1000.0);
        
        // scale psteps
//        tot_psteps=tmax*nparts;
//        double[] psteps2 = new double[N];
//        for (int i = 0; i < N; i++)
//        {
//            psteps2[i]=(double)psteps[i][1]/tot_psteps;
//        }       
    }

    public static double[][] setupStartLocationsFyneFarms()
    {
        int nsites = 9;
        double startlocs[][]= new double[nsites][3];
        
        startlocs[0][0]=1; startlocs[0][1]=357420; startlocs[0][2]=6217200; 
        startlocs[1][0]=2; startlocs[1][1]=361834; startlocs[1][2]=6223063;
        startlocs[2][0]=3; startlocs[2][1]=353078; startlocs[2][2]=6206339;
        startlocs[3][0]=4; startlocs[3][1]=354246; startlocs[3][2]=6194759; 
        startlocs[4][0]=5; startlocs[4][1]=352745; startlocs[4][2]=6201735;
        startlocs[5][0]=6; startlocs[5][1]=348880; startlocs[5][2]=6199380;
        startlocs[6][0]=7; startlocs[6][1]=354969; startlocs[6][2]=6193169;
        startlocs[7][0]=8; startlocs[7][1]=348606; startlocs[7][2]=6204475;
        startlocs[8][0]=9; startlocs[8][1]=352401; startlocs[8][2]=6190933;
        // non-fishfarms
//        double startlocs[][]=[354500 6188000; 
//            355200 6192000;
//            350000 6197000;
//            352800 6196000; 
//            348000 6209000;
//            354000 6204000;
//            357000 6213000;
//            360000 6219000;
//            370000 6227000];
        // "hypothetical" fishfarm used for SAMS newsletter
        //fishfarms(1,1)=351000;
        //fishfarms(1,2)=6195000;
        //"2" is Minard
        //"7" is Portavadie
        return startlocs;
    }

    public double[][] makeCentroidXY()
    {
        double[][] out=new double[1][1];
        return out;
    }

    public void setupOutput()
    {

    }

    public void writeOutput()
    {
    
    }
    
    public static double[][] readFileDoubleArray(String filename, int rows, int cols, String sep, boolean note)
    {
        double[][] myDouble = new double[rows][cols];
        int x=0, y=0;
        boolean failed = false;
        try
        {
            BufferedReader in = new BufferedReader(new FileReader(filename));	//reading files in specified directory
 
            String line;
            while ((line = in.readLine()) != null)	//file reading
            {
                y=0;
                if (x >= rows)
                {
                    System.out.println(filename+" has more rows than expected.");
                    break;
                }
                String[] values = line.split(" ");
                for (String str : values)
                {
                    if (y >= cols)
                    {
                        System.out.println(filename+" has more columns than expected: "+y);
                        break;
                    }
                    double str_double = Double.parseDouble(str);
                    myDouble[x][y]=str_double;
                    //System.out.print(myDouble[x][y] + " ");
                    y++;

                }
                //System.out.println("");
                x++;
            }
            in.close();
        } catch( IOException ioException ) {
            System.err.println("******************* Cannot read from file "+filename+" ******************************");
            failed = true;
        }
        if (note == true && failed == false)
        {
            System.out.printf("Created %dx%d array from file: %s\n",myDouble.length,myDouble[0].length,filename);
        }
        return myDouble;
    }
    
    public static int[][] readFileIntArray(String filename, int rows, int cols, String sep, boolean note)
    {
        int[][] myInt = new int[rows][cols];
        int x=0, y=0;
        boolean failed = false;
        try
        {
            BufferedReader in = new BufferedReader(new FileReader(filename));	//reading files in specified directory
            //System.out.println("in readFileIntArray");
            String line;
            while ((line = in.readLine()) != null)	//file reading
            {
                
                y=0;
                if (x >= rows)
                {
                    System.out.println(filename+" has more rows than expected.");
                    break;
                }
                String[] values = line.split(sep);
                for (String str : values)
                {
                    if (y >= cols)
                    {
                        System.out.println(filename+" has more columns than expected.");
                        break;
                    }
                    int str_int = Integer.parseInt(str);
                    myInt[x][y]=str_int;
                    //System.out.print(myDouble[x][y] + " ");
                    y++;

                }
                //System.out.println("");
                x++;
            }
            in.close();
        } catch( IOException ioException ) {
            System.err.println("******************* Cannot read from file "+filename+" ******************************");
            failed = true;
        }
        if (note == true && failed == false)
        {
            System.out.printf("Created %dx%d array from file: %s\n",myInt.length,myInt[0].length,filename);
        }
        return myInt;
    }
    
    public static void writeDoubleArrayToFile(double[][] variable, String filename)
    {
        try
        {
            // Create file 
            FileWriter fstream = new FileWriter(filename);
            PrintWriter out = new PrintWriter(fstream);
            for (int i = 0; i < variable.length; i++)
            {
                for (int j = 0; j < variable[0].length; j++)
                {
                    out.printf("%.4e ",variable[i][j]);
                }
                out.printf("\n");
            }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }
    public static void writeIntegerArrayToFile(int[][] variable, String filename)
    {
        try
        {
            // Create file 
            FileWriter fstream = new FileWriter(filename);
            PrintWriter out = new PrintWriter(fstream);
            for (int i = 0; i < variable.length; i++)
            {
                for (int j = 0; j < variable[0].length; j++)
                {
                    out.printf("%d ",variable[i][j]);
                }
                out.printf("\n");
            }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }
    public static void particleLocsToFile(Particle[] particles, String filename)
    {
        try
        {
            // Create file 
            FileWriter fstream = new FileWriter(filename);
            PrintWriter out = new PrintWriter(fstream);
            for (int i = 0; i < particles.length; i++)
            {
                out.printf("%f %f %d\n",particles[i].getLocation()[0],particles[i].getLocation()[1],particles[i].getElem());
            }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    public static void particleLocsToFile2(double time, Particle[] particles, int i, String filename)
    {
        try
        {
            // Create file 
            FileWriter fstream = new FileWriter(filename,true);
            PrintWriter out = new PrintWriter(fstream);
            out.printf("%f %f %f %d\n",time,particles[i].getLocation()[0],particles[i].getLocation()[1],particles[i].getElem());
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    } 
    
    public double nc_varget(String filename, String variable)
    {
        double out=0;
        return out;
    }

}
