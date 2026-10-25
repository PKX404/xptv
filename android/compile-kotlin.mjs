import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {delimiter,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=fileURLToPath(new URL('.',import.meta.url)),cache=join(root,'build/kotlin-tools');
await mkdir(cache,{recursive:true});
const dependencies=[
 ['org.jetbrains.kotlin','kotlin-compiler-embeddable','1.9.24'],
 ['org.jetbrains.kotlin','kotlin-stdlib','1.9.24'],
 ['org.jetbrains.kotlin','kotlin-script-runtime','1.9.24'],
 ['org.jetbrains.kotlin','kotlin-reflect','1.6.10'],
 ['org.jetbrains.kotlin','kotlin-daemon-embeddable','1.9.24'],
 ['org.jetbrains.intellij.deps','trove4j','1.0.20200330'],
 ['org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'],
 ['org.jetbrains','annotations','26.0.2']
];
const jars=await Promise.all(dependencies.map(async([group,name,version])=>{
 const filename=`${name}-${version}.jar`,target=join(cache,filename),url=`https://repo.maven.apache.org/maven2/${group.replaceAll('.','/')}/${name}/${version}/${filename}`;
 let expected;try{expected=(await readFile(target+'.sha1','utf8')).trim();}catch{const response=await fetch(url+'.sha1',{signal:AbortSignal.timeout(60000)});if(!response.ok)throw Error(`Checksum unavailable: ${name}`);expected=(await response.text()).trim();if(!/^[a-f0-9]{40}$/i.test(expected))throw Error(`Invalid checksum: ${name}`);await writeFile(target+'.sha1',expected);}
 try{const bytes=await readFile(target);if(createHash('sha1').update(bytes).digest('hex')===expected)return target;}catch{}
 const response=await fetch(url,{signal:AbortSignal.timeout(180000)});if(!response.ok)throw Error(`Download failed: ${name}`);const bytes=Buffer.from(await response.arrayBuffer());if(createHash('sha1').update(bytes).digest('hex')!==expected)throw Error(`Checksum mismatch: ${name}`);await writeFile(target,bytes);return target;
}));
const platform=process.argv[2]||join(process.env.XPARTY_ANDROID_SDK||'','platforms/android-35/android.jar');
const java=process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const classes=join(root,'build/classes'),output=join(root,'build/kotlin-classes'),stdlib=jars[1];
await mkdir(output,{recursive:true});
const result=spawnSync(java,['-Xmx1024m','-cp',jars.join(delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
 '-no-stdlib','-no-reflect','-jvm-target','1.8','-classpath',[resolve(platform),classes,stdlib,jars[7]].join(delimiter),
 '-d',output,join(root,'app/src/main/java/com/pkx404/xparty/MiniProfileDialog.kt')],{stdio:'inherit'});
if(result.error)throw result.error;if(result.status!==0)process.exit(result.status||1);
await writeFile(join(root,'build/kotlin-stdlib.path'),stdlib);
