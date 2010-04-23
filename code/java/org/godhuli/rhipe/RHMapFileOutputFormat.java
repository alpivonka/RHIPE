/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.godhuli.rhipe;

import java.io.IOException;
import java.util.Arrays;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileUtil;

import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.mapreduce.Partitioner;
import java.io.FileNotFoundException;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;


public class RHMapFileOutputFormat 
extends FileOutputFormat<RHBytesWritable, RHBytesWritable> {
    private final static HashPartitioner<RHBytesWritable,RHBytesWritable> hp = 
	new HashPartitioner<RHBytesWritable,RHBytesWritable>();

    public RecordWriter<RHBytesWritable, RHBytesWritable> 
	getRecordWriter(TaskAttemptContext context
			) throws IOException, InterruptedException {
	Configuration conf = context.getConfiguration();
	Path file = getDefaultWorkFile(context, "");
	FileSystem fs = file.getFileSystem(conf);
	CompressionCodec codec = null;
	CompressionType compressionType = CompressionType.NONE;
	if (getCompressOutput(context)) {
	    compressionType = SequenceFileOutputFormat.getOutputCompressionType(context);
	    Class<? extends CompressionCodec> codecClass = 
		getOutputCompressorClass(context,
					 DefaultCodec.class);
	    codec = ReflectionUtils.newInstance(codecClass, conf);
	}
	// ignore the progress parameter, since MapFile is local
	final MapFile.Writer out =
	    new MapFile.Writer(conf, fs, file.toString(),
			       RHBytesWritable.class,
			       RHBytesWritable.class,
			       compressionType, codec,
			       context);
	return new RecordWriter<RHBytesWritable, RHBytesWritable>() {
	    public void write(RHBytesWritable key, RHBytesWritable value)
		throws IOException {
		out.append(key, value);
	    }
	    public void close(TaskAttemptContext context) throws IOException { out.close();}
	};
    }

  /** Open the output generated by this format. */
    public static MapFile.Reader[] getReaders(Path dir,
					      Configuration conf)
	throws IOException,FileNotFoundException {
	FileSystem fs = FileSystem.get(conf);
	// Path[] names = FileUtil.stat2Paths(fs.listStatus(dir));
	FileStatus[] srcs = fs.globStatus(dir);
	if (srcs==null || srcs.length==0) {
	    throw new FileNotFoundException("Cannot access " + dir + 
					    ": No such file or directory.");
	}
	// if(srcs.length==1 && srcs[0].isDir())
	//     srcs = fs.listStatus(srcs[0].getPath());

	Path[] names = new Path[ srcs.length];
	for(int i=0;i< names.length;i++){
	    names[i] = srcs[i].getPath();
	}
	// sort names, so that hash partitioning works
	Arrays.sort(names);
	
	MapFile.Reader[] parts = new MapFile.Reader[names.length];
	for (int i = 0; i < names.length; i++) {
	    parts[i] = new MapFile.Reader(fs, names[i].toString(), conf);
	}
	return parts;
    }
    

    public static MapFile.Reader[] getReaders(String[] ps,
					      Configuration conf)
	throws IOException,FileNotFoundException {
	FileSystem fs = FileSystem.get(conf);
	Path[] names = new Path[ ps.length];
	for(int i=0;i< names.length;i++){
	    names[i] = new Path(ps[i]);
	}
	// sort names, so that hash partitioning works
	Arrays.sort(names);
	
	MapFile.Reader[] parts = new MapFile.Reader[names.length];
	for (int i = 0; i < names.length; i++) {
	    parts[i] = new MapFile.Reader(fs, names[i].toString(), conf);
	}
	return parts;
    }

    /** Get an entry from output generated by this class. */
    public static 
	RHBytesWritable getEntry(MapFile.Reader[] readers,
		    Partitioner<RHBytesWritable, RHBytesWritable> partitioner,
		    RHBytesWritable key,
		    RHBytesWritable value) throws IOException {
	int part = partitioner.getPartition(key, value, readers.length);
	return (RHBytesWritable)readers[part].get(key, value);
    }
    public static 
	RHBytesWritable getEntry(MapFile.Reader[] readers,
		    RHBytesWritable key,
		    RHBytesWritable value) throws IOException {
	int part = hp.getPartition(key, value, readers.length);
	return (RHBytesWritable)readers[part].get(key, value);
	// RHBytesWritable af= (RHBytesWritable)readers[part].get(key, value);
	// // System.out.println("Part for "+key+" is "+part+" and returned is null? "+ (af==null? "YES":"NO"));
	// // System.out.println("Value="+value.toByteString());
	// return(af);
    }
    public static 
	MapFile.Reader getPartForKey(MapFile.Reader[] readers,
		    RHBytesWritable key,
		    RHBytesWritable value) throws IOException {
	int part = hp.getPartition(key, value, readers.length);
	return readers[part];
	// return (RHBytesWritable)readers[part].get(key, value);
    }


}

