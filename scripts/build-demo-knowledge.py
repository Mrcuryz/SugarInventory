"""Build an independently authored demonstration corpus with real local embeddings.

Never reads the private enterprise corpus. Run with agent-service on PYTHONPATH.
"""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile
from xml.sax.saxutils import escape

from app.rag.contracts import *
from app.rag.offline.chunker import build_chunks, CHUNKER_VERSION, _chunk_manifest_hash
from app.rag.offline.normalizer import _with_document_hash, _document_manifest_sha256
from app.rag.offline.embedding_provider import FastEmbedLocalProvider
from app.rag.offline.index_builder import build_indexes
from app.rag.runtime.hybrid_retriever import HybridIndex
from app.rag.offline.retrieval_evaluator import evaluate_retrieval, write_evaluation_report
from app.rag.offline.publisher import publish_release

# These texts are original demonstration material, not anonymized private documents.
SECTIONS = [
 ('演示企业介绍', '示例食品企业是用于展示数字仓储平台的虚构企业。演示内容涵盖冰糖及食品加工厂的库存、库位、扫码和生产记录管理，不代表任何真实企业的规模、历史、客户、荣誉或经营情况。知识库不收录真实企业介绍、配方、生产工艺或工艺参数。',
  ['演示企业介绍是什么','示例食品企业是真实公司吗','演示公司有什么背景','演示企业有没有真实客户资料','这家演示企业的经营情况是真实的吗','演示企业名称是什么']),
 ('库存查询说明', '库存查询在仓库管理的库存汇总页面进行。按产品名称、产品类型和状态筛选后点击查询，可以切换产品汇总库存、二维码库存和生产中半成品。板数、件数与重量属于不同计量口径，核对时应确认单位。',
  ['库存查询在哪个页面','库存汇总可以按什么条件筛选','怎么查看二维码库存','怎么查询生产中半成品','库存板数和重量有什么区别','怎样按产品名称查询库存']),
 ('库位查询说明', '库位查询用于了解货物存放位置。可以从库存记录进入平面图或位置入口，查看库位及关联货物。库位状态与货物数量应结合库存明细核对。移动货物后，应按实际作业完成系统中的对应任务。',
  ['怎么查货物存放的库位','平面图可以查看什么','库存记录如何查询位置','库位状态需要与什么核对','移动货物后怎么登记','库位查询有什么用途']),
 ('二维码追溯说明', '二维码管理支持按二维码、产品名称、当前状态和日期查询。每条货物记录可以关联任务、位置、化验和流转信息。标签按页面提供的功能下载或导出；直接打印需要配置打印助手及适配的打印设备。',
  ['二维码可以关联什么信息','二维码管理如何按状态查询','货物的流转信息在哪看','二维码标签如何下载','直接打印标签需要什么','二维码查询可以用哪些条件']),
 ('入库操作说明', '入库操作应先确认目标库位，再扫描待入库托盘，核对产品、数量和位置后确认。重复扫码或不符合条件的记录应先检查提示。确认完成后查看任务状态与库存记录，避免漏记或重复登记。',
  ['扫码入库先做什么','入库确认前核对哪些信息','重复扫码应该怎么处理','入库完成后查看什么','怎么避免入库重复登记','待入库托盘如何核对']),
 ('生产日报使用说明', '生产日报按日期和车间填报。填写后先保存，核对后按权限提交；历史记录按日期查询。日报支持表格导入导出。日报填报数据与生产订单登记产出是不同来源，统计对比应确认日期、车间和单位。',
  ['生产日报按什么填报','日报填写后如何保存提交','历史生产日报怎么查','日报支持表格导出吗','日报与生产订单产出能直接相加吗','生产日报对比需要确认哪些条件']),
 ('化验关联说明', '化验中心用于查询化验记录、批量化验组和标准。库存和二维码记录可以联查化验信息。缺少结果时先核实是否已录入以及关联对象是否正确。演示资料不提供配方、工艺参数或质量控制阈值。',
  ['化验中心能查询什么','库存如何联查化验信息','二维码可以关联化验吗','缺少化验结果应检查什么','批量化验组在哪查询','演示化验说明包含工艺参数吗']),
 ('账号权限与助手说明', '不同账号根据角色权限使用菜单和业务功能。AI 助手支持用日常语言查询业务，问题不明确时需要补充对象、日期或库位。知识查询仅提供已授权的演示资料。业务数据以系统记录为准，重要判断应核对原始业务记录。',
  ['为什么账号看到的菜单不同','AI助手怎样查询业务','助手追问时需要补充什么','知识查询资料的范围是什么','AI回答应该与什么核对','账号功能由什么决定']),
]

def sha(data):
    return hashlib.sha256(data).hexdigest()

def write(path, model):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(model.model_dump_json(indent=2)+'\n', encoding='utf-8')

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--model-path', type=Path, required=True)
    args=ap.parse_args()
    version='demo-knowledge-2026-09-08-v1'
    root=args.output/version
    root.mkdir(parents=True, exist_ok=False)
    now=datetime.now(timezone.utc)
    review=sha(json.dumps(SECTIONS,ensure_ascii=False).encode())
    documents=[]
    for i,(title,body,queries) in enumerate(SECTIONS,1):
        source=root/'sources'/f'demo-{i:02}.docx'
        source.parent.mkdir(exist_ok=True)
        with ZipFile(source,'w') as z:
            z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
            z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
            z.writestr('word/document.xml','<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+''.join('<w:p><w:r><w:t>'+escape(t)+'</w:t></w:r></w:p>' for t in [title,body])+'</w:body></w:document>')
        doc=DocumentContract(documentId=f'doc-demo-{i:02}',displayTitle=title,sourceFileName=source.name,
            sourceType='DOCX',documentType='COMPANY_PROFILE',knowledgeDomain='COMPANY',status='ACTIVE',
            sourceBasis='independently-authored-demo',sourceSha256=sha(source.read_bytes()),
            pages=(PageContract(pageNumber=1,title=title,sourceText=body,normalizedText=body,
                sectionKind='COMPANY_PROFILE',knowledgeDomains=('COMPANY',)),),
            extraction=ExtractionContract(parserVersion='authored-text-v1',processedAt=now,status='SUCCEEDED',
                sourceExtractionSha256=sha(body.encode()),reviewProfileSha256=review),documentSha256='0'*64)
        doc=_with_document_hash(doc); documents.append(doc)
        write(root/'documents'/f'{doc.documentId}.document.json',doc)
    dh=_document_manifest_sha256(documents)
    chunks=build_chunks(tuple(documents))
    cm=ChunkManifest(corpusVersion=version,generatedAt=now,chunkerVersion=CHUNKER_VERSION,
        documentManifestSha256=dh,documentCount=len(documents),chunkCount=len(chunks),
        chunkTypeCounts={t:sum(c.chunkType==t for c in chunks) for t in ChunkType},
        chunks=tuple(ChunkSummary(chunkId=c.chunkId,documentId=c.documentId,chunkType=c.chunkType,
            contentSha256=c.contentSha256,embeddingRef=c.embeddingRef or '') for c in chunks),chunkManifestSha256='0'*64)
    cm=cm.model_copy(update={'chunkManifestSha256':_chunk_manifest_hash(cm)})
    write(root/'chunks/chunk-manifest.json',cm)
    (root/'chunks/chunks.jsonl').write_text(''.join(c.model_dump_json()+'\n' for c in chunks),encoding='utf-8')
    write(root/'corpus-manifest.json',CorpusManifest(corpusVersion=version,status='VALIDATED',builtAt=now,
        sourceBasis='independently-authored-demo',documentCount=len(documents),chunkCount=len(chunks),
        documentManifestSha256=dh,inventorySha256=sha(''.join(d.sourceSha256 for d in documents).encode()),
        reviewProfileSha256=review,chunkManifestSha256=cm.chunkManifestSha256))
    provider=FastEmbedLocalProvider(args.model_path)
    build_indexes(root,provider)
    cases=[]
    for i,(_,_,queries) in enumerate(SECTIONS,1):
        for query in queries:
            cases.append(RetrievalEvaluationCase(caseId=f'demo-case-{len(cases)+1:03}',category='DEMO_CONTENT',
                query=query,expectedDocumentIds=(f'doc-demo-{i:02}',),requiredRank=3,topK=3))
    for i in range(12):
        cases.append(RetrievalEvaluationCase(caseId=f'demo-case-{len(cases)+1:03}',category='ACCESS_CONTROL',
            query=SECTIONS[i%8][2][i//8],role='USER',expectedStatus='FORBIDDEN'))
    (root/'qa').mkdir(exist_ok=True)
    (root/'qa/evaluation-cases.jsonl').write_text(''.join(c.model_dump_json()+'\n' for c in cases),encoding='utf-8')
    report=evaluate_retrieval(HybridIndex(root,provider),tuple(cases))
    write_evaluation_report(root,report)
    print(json.dumps({'cases':report.caseCount,'passed':report.passedCount,'failed':report.failedCount}))
    if report.failedCount:
        print(report.model_dump_json(indent=2)); raise SystemExit('Evaluation failed')
    publish_release(root,args.output/'runtime',published_by='demo-privacy-build')
    print(args.output/'runtime')

if __name__=='__main__': main()
