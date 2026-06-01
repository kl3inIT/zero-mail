---
status: resolved
trigger: "Loi UI phan Huy dang ky: noi dung trong hang chi tiet bi chong chu."
created: "2026-05-25T17:01:34.6115394+07:00"
updated: "2026-05-25T17:06:58.9111334+07:00"
---

# Debug Session: Huy dang ky layout overlap

## Symptoms

- expected_behavior: "Phan Huy dang ky hien thi theo cot ro rang, copy khong tran sang cot An toan."
- actual_behavior: "Copy ben duoi cot Email se xu ly tran sang cot An toan trong screenshot nguoi dung gui."
- error_messages: "Khong co console error trong screenshot; day la loi visual layout."
- timeline: "Nguoi dung bao loi ngay 2026-05-25."
- reproduction: "Mo UI danh sach nguoi gui/phan tich co hang kling@user-service.klingai.com va suggestion Huy dang ky."

## Current Focus

- hypothesis: "Grid/detail row does not constrain the processing column width or long Vietnamese copy, allowing text to overlap the safety column."
- test: "Inspect the component that renders unsubscribe sender detail rows and verify layout after constraining grid/flex text wrapping."
- expecting: "Text wraps/truncates inside its own column and the safety column remains readable."
- next_action: "Locate Huy dang ky/unsubscribe UI component."

## Evidence

- timestamp: "2026-05-25T17:01:34.6115394+07:00"
  observation: "Screenshot shows 'Neu huy nhan thanh cong...' text overlapping the 'Khong co gan day...' safety text."
- timestamp: "2026-05-25T17:06:58.9111334+07:00"
  observation: "Targeted lint passed for CandidateListTable.tsx and cleanup-unsubscribe-campaign.spec.ts. Playwright unsubscribe campaign spec passed on desktop and mobile."

## Eliminated

## Resolution

- root_cause: "Expanded detail row used the shared TableCell default whitespace-nowrap, so long Vietnamese explanatory text inherited nowrap and painted across adjacent grid columns."
- fix: "Set the expanded detail cell to whitespace-normal and constrained detail grid items with min-w-0 plus break-words text."
- verification: "pnpm --dir apps/web lint -- features/cleanup/unsubscribe-campaign/components/CandidateListTable.tsx e2e/cleanup-unsubscribe-campaign.spec.ts; pnpm --dir apps/web test:e2e e2e/cleanup-unsubscribe-campaign.spec.ts --reporter=line"
- files_changed: "apps/web/features/cleanup/unsubscribe-campaign/components/CandidateListTable.tsx; apps/web/e2e/cleanup-unsubscribe-campaign.spec.ts"
