import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side

font_section = Font(name='Calibri', size=11, bold=True, color='1F497D')
fill_section = PatternFill(start_color='F2F5F9', end_color='F2F5F9', fill_type='solid')

# 1. FIX INTEGRATION TEST
wb_int = openpyxl.load_workbook(r'D:\Download\SE2056-JV - Group 5 - Template3.1_Integration-Test.xlsx')

# Fix Feature Name1
ws1 = wb_int['Feature Name1']
# Clear R16 completely, then set section header
for c in range(1, 15):
    ws1.cell(16, c).value = None
ws1.cell(16, 2, value='Function B: Đăng nhập & Điều hướng phân quyền đa vai trò (Spring Security)').font = font_section

# Clear R21 completely, then set section header
for c in range(1, 15):
    ws1.cell(21, c).value = None
ws1.cell(21, 2, value='Function C: Quên mật khẩu & Đặt lại mật khẩu mới').font = font_section

# Also make R11 title in col B so it matches
ws1.cell(11, 1).value = None
ws1.cell(11, 2, value='Function A: Đăng ký & Kích hoạt tài khoản qua Email OTP').font = font_section

# Fix Feature Name2
ws2 = wb_int['Feature Name2']
ws2.cell(11, 1).value = None
ws2.cell(11, 2, value='Function A: Khách hàng tạo & theo dõi khiếu nại (Customer Dispute)').font = font_section

for c in range(1, 15):
    ws2.cell(16, c).value = None
ws2.cell(16, 2, value='Function B: CS Staff tiếp nhận & giải quyết ticket khiếu nại').font = font_section

# Fix Creator display in Cover & Test Statistics
wb_int['Cover']['F4'] = 'Bùi Xuân Quý'
wb_int['Test Statistics']['E3'] = 'Bùi Xuân Quý'

wb_int.save(r'D:\Download\SE2056-JV - Group 5 - Template3.1_Integration-Test.xlsx')
try:
    wb_int.save(r'D:\Download\SE2056-JV - Group x  - Template3.1_Integration-Test.xlsx')
except Exception:
    pass

# 2. FIX SYSTEM TEST
wb_sys = openpyxl.load_workbook(r'D:\Download\SE2056-JV - Group 5 - Template3.2_System-Test.xlsx')

ws_w1 = wb_sys['Workflow Name1']
ws_w1.cell(11, 1).value = None
ws_w1.cell(11, 2, value='Scenario A: Đăng ký tài khoản & Kích hoạt qua mã OTP Email thực tế').font = font_section

for c in range(1, 15):
    ws_w1.cell(16, c).value = None
ws_w1.cell(16, 2, value='Scenario B: Đăng nhập hệ thống & Điều hướng phân quyền đa vai trò').font = font_section

for c in range(1, 15):
    ws_w1.cell(21, c).value = None
ws_w1.cell(21, 2, value='Scenario C: Quên mật khẩu & Khôi phục quyền truy cập tài khoản').font = font_section

ws_w2 = wb_sys['Workflow Name2']
ws_w2.cell(11, 1).value = None
ws_w2.cell(11, 2, value='Scenario A: Khách hàng tạo phiếu khiếu nại cho đơn hàng đã nhận').font = font_section

for c in range(1, 15):
    ws_w2.cell(15, c).value = None
ws_w2.cell(15, 2, value='Scenario B: CS Staff tiếp nhận ticket & Giải quyết tranh chấp').font = font_section

wb_sys['Cover']['F4'] = 'Bùi Xuân Quý'
wb_sys['Test Statistics']['E3'] = 'Bùi Xuân Quý'

wb_sys.save(r'D:\Download\SE2056-JV - Group 5 - Template3.2_System-Test.xlsx')
try:
    wb_sys.save(r'D:\Download\SE2056-JV - Group x  - Template3.2_System-Test.xlsx')
except Exception:
    pass

print('Successfully cleaned up and optimized both test workbooks!')
