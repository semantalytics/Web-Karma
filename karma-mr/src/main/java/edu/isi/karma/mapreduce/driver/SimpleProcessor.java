package edu.isi.karma.mapreduce.driver;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileAsTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class SimpleProcessor extends Configured implements Tool {

	 public Job configure(Properties p ) throws Exception
	 {
		
		Configuration conf = getConf();
		conf.set("fs.default.name", p.getProperty("fs.default.name"));
		conf.set("mapred.job.tracker", p.getProperty("mapred.job.tracker"));
		conf.set("model.uri", p.getProperty("model.uri"));
		if(p.getProperty("KARMA_USER_HOME") != null)
		{
			conf.set("KARMA_USER_HOME", p.getProperty("KARMA_USER_HOME"));
		}
		Job job = new Job(getConf());
        job.setInputFormatClass(SequenceFileAsTextInputFormat.class);
        job.setJarByClass(SimpleProcessor.class);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);
        job.setMapperClass(SimpleMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.setInputPaths(job, new Path(p.getProperty("input.directory")));
        FileOutputFormat.setOutputPath(job, new Path(p.getProperty("output.directory")));
        
        job.setNumReduceTasks(0);
        return job;
	 }
	 
	 public int run(String[] args) throws Exception {
         // Configuration processed by ToolRunner
		 Properties p = new Properties();
		 p.load(new FileInputStream(new File(args[0])));
		 
         
         Job job = configure(p);
         
         if(!job.waitForCompletion(false))
         {
        	 System.err.println("Unable to finished job");
        	 return -1;
         }
        
         return 0;
       }
       
       public static void main(String[] args) throws Exception {
         // Let ToolRunner handle generic command-line options 
         int res = ToolRunner.run(new Configuration(), new SimpleProcessor(), args);
         
         System.exit(res);
       }

}
