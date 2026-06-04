'use client';

import { Fragment } from 'react';
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { DayPicker, useDayPicker, type MonthCaptionProps } from 'react-day-picker';

import { cn } from '@/lib/utils';

// Token-driven shadcn-style wrapper around react-day-picker v10. All visual classes are merged
// into the picker's slot map so palette pivots in `globals.css` flow into the calendar without
// pulling in the default `react-day-picker/style.css`. Range / multi-month behaviour is owned by
// callers via the `{...props}` passthrough.
function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  numberOfMonths = 1,
  ...props
}: React.ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      numberOfMonths={numberOfMonths}
      className={cn('p-3', className)}
      classNames={{
        months: 'flex flex-col gap-4 sm:flex-row sm:gap-6',
        month: 'flex flex-col gap-3',
        month_caption: 'flex h-8 items-center justify-center gap-2',
        caption_label: 'text-sm font-medium',
        month_grid: 'w-full border-collapse',
        weekdays: 'flex',
        weekday: 'text-muted-foreground w-9 text-[0.75rem] font-normal',
        week: 'flex w-full mt-1',
        day: 'h-9 w-9 p-0 text-center text-sm align-middle relative focus-within:relative focus-within:z-20',
        day_button:
          'hover:bg-accent hover:text-accent-foreground inline-flex size-9 items-center justify-center rounded-md p-0 text-sm font-normal aria-selected:opacity-100 transition-colors',
        selected:
          'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground focus:bg-primary focus:text-primary-foreground',
        today: 'bg-accent text-accent-foreground',
        outside: 'text-muted-foreground/50 aria-selected:text-muted-foreground/50',
        disabled: 'text-muted-foreground/40 opacity-50',
        range_start:
          'bg-primary text-primary-foreground rounded-l-md rounded-r-none [&_button]:rounded-l-md [&_button]:rounded-r-none',
        range_end:
          'bg-primary text-primary-foreground rounded-r-md rounded-l-none [&_button]:rounded-r-md [&_button]:rounded-l-none',
        range_middle:
          'bg-accent text-accent-foreground rounded-none [&_button]:rounded-none [&_button]:hover:bg-accent [&_button]:hover:text-accent-foreground',
        hidden: 'invisible',
        ...classNames,
      }}
      components={{
        // Replace the default top-level Nav (which renders one chevron at each far edge of the
        // popover and looked detached from the month labels) with chevrons that sit directly
        // adjacent to each month's caption text. Default Nav is rendered as an empty Fragment
        // (the slot's prop type requires an Element so `null` is rejected); MonthCaption draws
        // the matching prev/next button beside its own label.
        Nav: () => <Fragment />,
        MonthCaption: (captionProps: MonthCaptionProps) => (
          <NavInlineMonthCaption {...captionProps} totalMonths={numberOfMonths} />
        ),
      }}
      {...props}
    />
  );
}

function NavInlineMonthCaption({
  calendarMonth,
  displayIndex,
  totalMonths,
}: MonthCaptionProps & { totalMonths: number }) {
  const dayPicker = useDayPicker();
  const isFirst = displayIndex === 0;
  const isLast = displayIndex === totalMonths - 1;
  const { previousMonth, nextMonth, goToMonth } = dayPicker;
  // Locale-aware "June 2025" / "Tháng 6 2025" rendering — matches the rest of the dialog and
  // means we don't have to depend on react-day-picker's optional formatters.
  const captionLabel = calendarMonth.date.toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  });
  const navButtonClass =
    'hover:bg-accent hover:text-accent-foreground disabled:opacity-40 disabled:hover:bg-transparent inline-flex size-7 items-center justify-center rounded-md transition-colors';

  return (
    <div className="flex h-8 items-center justify-center gap-2">
      {isFirst ? (
        <button
          type="button"
          aria-label="Previous month"
          className={navButtonClass}
          disabled={!previousMonth}
          onClick={() => previousMonth && goToMonth(previousMonth)}
        >
          <ChevronLeftIcon className="size-4" aria-hidden="true" />
        </button>
      ) : (
        // Spacer keeps the caption text centered on inner months that don't own a chevron, so
        // "June 2025" and "July 2025" line up identically across the multi-month layout.
        <span className="size-7" aria-hidden="true" />
      )}
      <span className="text-sm font-medium">{captionLabel}</span>
      {isLast ? (
        <button
          type="button"
          aria-label="Next month"
          className={navButtonClass}
          disabled={!nextMonth}
          onClick={() => nextMonth && goToMonth(nextMonth)}
        >
          <ChevronRightIcon className="size-4" aria-hidden="true" />
        </button>
      ) : (
        <span className="size-7" aria-hidden="true" />
      )}
    </div>
  );
}

export { Calendar };
