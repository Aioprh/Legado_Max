#!/usr/bin/env python3
"""
AutoSource CLI - 自动生成并验证「可用」的 Legado 书源

用法：
    python auto_source_cli.py gen-search --search-url "https://site.com/search?q={{key}}" --keyword "斗破苍穹" [--name 站点名] [--out out.json]
    python auto_source_cli.py gen-book  --book-url "https://site.com/book/123" [--name 站点名] [--out out.json]

流程：抓真实页面 → 从真实 DOM 推导规则 → 组装书源 → 用 DebugEngine 全链路验证 → 输出 JSON。
只有验证通过（ok=true）的书源才被认为「可用」。
"""

import sys
import os
import json
import argparse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from debugger.engine.auto_source import auto_generate


def _print_report(report: Dict):
    print("\n" + "=" * 64)
    print("自动生成书源报告")
    print("=" * 64)
    print(f"总体结果: {'✓ 可用' if report.get('ok') else '✗ 失败'}")

    if report.get('errors'):
        print("\n错误:")
        for e in report['errors']:
            print(f"  - {e}")

    if report.get('detect_search'):
        d = report['detect_search']
        print(f"\n[搜索列表] 模式={'JSON' if d['json_mode'] else 'HTML'}")
        print(f"  bookList: {d['selector']}")
        print(f"  name:     {d['name_rule']}")
        print(f"  bookUrl:  {d['url_rule']}")
        print(f"  采样 {d['sample_count']} 条: {[s['name'] for s in d['sample'][:5]]}")

    if report.get('detect_toc'):
        d = report['detect_toc']
        print(f"\n[目录列表] 模式={'JSON' if d['json_mode'] else 'HTML'}")
        print(f"  chapterList: {d['selector']}")
        print(f"  chapterName: {d['name_rule']}")
        print(f"  chapterUrl:  {d['url_rule']}")
        print(f"  采样 {d['sample_count']} 章: {[s['name'] for s in d['sample'][:5]]}")

    if report.get('detect_content'):
        print(f"\n[正文] content: {report['detect_content']['selector']}")

    if report.get('steps'):
        print("\n[验证步骤]")
        for name, st in report['steps'].items():
            mark = '✓' if st.get('ok') else '✗'
            extra = ''
            if name == 'toc' and 'count' in st:
                extra = f" ({st['count']}章)"
            if name == 'content' and 'len' in st:
                extra = f" ({st['len']}字符)"
            print(f"  [{mark}] {name}: {st.get('msg', '')}{extra}")

    if report.get('bookSource'):
        print(f"\n[书源] {report['bookSource'].get('bookSourceName')}")

    print("=" * 64)


def cmd_gen_search(args):
    if '{{key}}' not in args.search_url:
        print("提示: --search-url 中应含 {{key}} 占位符（将替换为关键词）")
    report = auto_generate(build_from_search=True, search_url=args.search_url,
                           keyword=args.keyword, source_name=args.name, validate=True)
    _print_report(report)
    _write_output(report, args.out)
    sys.exit(0 if report.get('ok') else 1)


def cmd_gen_book(args):
    report = auto_generate(build_from_search=False, book_url=args.book_url,
                           source_name=args.name, validate=True)
    _print_report(report)
    _write_output(report, args.out)
    sys.exit(0 if report.get('ok') else 1)


def _write_output(report: Dict, out: str):
    if not out:
        return
    with open(out, 'w', encoding='utf-8') as f:
        json.dump([report['bookSource']], f, ensure_ascii=False, indent=2)
    print(f"\n书源已写入: {out}")


def main():
    parser = argparse.ArgumentParser(
        description='Legado 书源自动生成器（抓真实页面→推导规则→全链路验证）',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例：
  # 从搜索页自动生成（含搜索规则）
  python auto_source_cli.py gen-search --search-url "https://site.com/search?q={{key}}" --keyword "斗破苍穹" --out bs.json

  # 只给书籍详情页，自动推导目录+正文（无搜索）
  python auto_source_cli.py gen-book --book-url "https://site.com/book/123" --out bs.json
        """
    )
    sub = parser.add_subparsers(dest='command', required=True)

    ps = sub.add_parser('gen-search', help='从搜索页生成书源')
    ps.add_argument('--search-url', required=True, help='搜索 URL，需含 {{key}} 占位符')
    ps.add_argument('--keyword', '-k', default='斗破苍穹', help='用于验证的搜索关键词')
    ps.add_argument('--name', help='书源名称（默认取域名）')
    ps.add_argument('--out', help='输出 JSON 文件路径')
    ps.set_defaults(func=cmd_gen_search)

    pb = sub.add_parser('gen-book', help='从书籍详情页生成书源（无搜索）')
    pb.add_argument('--book-url', required=True, help='书籍详情/目录页 URL')
    pb.add_argument('--name', help='书源名称（默认取域名）')
    pb.add_argument('--out', help='输出 JSON 文件路径')
    pb.set_defaults(func=cmd_gen_book)

    args = parser.parse_args()
    args.func(args)


if __name__ == '__main__':
    main()