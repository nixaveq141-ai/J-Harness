// Static compiler-AST inventory, not a sandbox for computed program behavior.
import ts from '../frontend/node_modules/typescript/lib/typescript.js'
import { readFileSync, statSync } from 'node:fs'
const path=process.argv[2]
if(!path || statSync(path).size>1024*1024)throw Error('TypeScript inventory input limit')
const source=ts.createSourceFile(path,readFileSync(path,'utf8'),ts.ScriptTarget.Latest,true)
if(source.parseDiagnostics.length)throw Error('Invalid TypeScript inventory source')
const modules=[]
function visit(node){
 if(ts.isImportDeclaration(node)||ts.isExportDeclaration(node)){
  if(node.moduleSpecifier){
   if(!ts.isStringLiteral(node.moduleSpecifier))throw Error('Computed module specifier')
   modules.push(node.moduleSpecifier.text)
  }
 }
 if(ts.isImportEqualsDeclaration(node))throw Error('Unsupported import assignment')
 if(ts.isCallExpression(node) && (node.expression.kind===ts.SyntaxKind.ImportKeyword || /(?:^|\.)(?:require|eval|Function)$/.test(node.expression.getText(source))))throw Error('Dynamic TypeScript execution/import')
 ts.forEachChild(node,visit)
}
visit(source)
console.log(JSON.stringify(modules))
