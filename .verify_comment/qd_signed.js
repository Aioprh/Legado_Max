// 复刻 QidianParagraphComment.signedGet 的签名逻辑，供探测脚本复用。
const crypto=require('crypto'),https=require('https');const{HttpsProxyAgent}=require('https-proxy-agent');
const API='https://druidv6.if.qidian.com/argus/api/';
const UA='Mozilla/mobile QDReaderAndroid/7.9.378/1436/1000009/Android';
function tdes(r,k,i){const c=crypto.createCipheriv('des-ede3-cbc',Buffer.from(k,'utf8'),Buffer.from(i,'binary'));return Buffer.concat([c.update(Buffer.from(r,'utf8')),c.final()]).toString('base64');}
const md5=v=>crypto.createHash('md5').update(v,'utf8').digest('hex');
const qm='20260930120000000aa:bb:cc:dd:ee:ff123456',mo='PFJM10';
function hd(p){const t=String(Date.now());const s=p.split('&').sort().join('&');const sr='Rv1rPTnczce|'+t+'|0|'+qm+'||||'+md5(s.toLowerCase())+'|f189adc92b816b3e9da29ea304d4a7e4';const sg=tdes(sr,'{1dYgqE)h9,R)hKqEcv4]k[h','01234567');const ir=qm+'|7.9.378|1080|1184|1000009|10|1|'+mo+'|1436|1000009|4|0|'+t+'|1|'+qm+'|||||0';return{QDSign:sg,QDInfo:tdes(ir,'0821CAAD409B84020821CAAD','\u0000'.repeat(8)),tstamp:t,'User-Agent':UA};}
const ag=process.env.HTTPS_PROXY?new HttpsProxyAgent(process.env.HTTPS_PROXY):undefined;
const g=(u,h)=>new Promise(r=>{const U=new URL(u);const q=https.request({hostname:U.hostname,path:U.pathname+U.search,headers:h,agent:ag},x=>{let d='';x.on('data',c=>d+=c);x.on('end',()=>r({c:x.statusCode,d}))});q.on('error',e=>r({c:-1,d:'ERR '+e.message}));q.end()});
module.exports={API,UA,hd,g,tdes,md5};